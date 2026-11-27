package com.sched.admin.aspect;

// ===== Jackson：负责把入参/返回值转成 JSON 并做脱敏 =====
import com.fasterxml.jackson.databind.JsonNode;         // JSON 节点的通用父类型（Object/Array/Text 都继承它）
import com.fasterxml.jackson.databind.ObjectMapper;     // Jackson 总入口，负责对象 <-> JSON 互转
import com.fasterxml.jackson.databind.node.ArrayNode;   // JSON 数组节点，对应 [ ... ]
import com.fasterxml.jackson.databind.node.ObjectNode;  // JSON 对象节点，对应 { ... }
import com.fasterxml.jackson.databind.node.TextNode;    // JSON 字符串节点，用来塞"不可序列化"的占位文本

// ===== 业务相关：注解、服务、实体、异常、当前用户 =====
import com.sched.annotation.OperLog;                    // 自定义注解，标记"这个方法要记操作日志"
import com.sched.core.service.operlog.OperLogService;   // 真正把日志写进数据库的服务（core 层）
import com.sched.core.support.BizException;             // 业务异常，带 code，可以安全地记录 message
import com.sched.core.support.CurrentUserProvider;      // 抽象"当前登录用户"，方便以后换成 JWT/Session 实现
import com.sched.dao.entity.operlog.OperLogRecord;             // 操作日志表的实体，和 t_oper_log 对应
import com.sched.enmu.OperResultStatus;                 // 操作结果：成功 1 / 失败 0

// ===== Servlet：取请求信息 + 过滤掉不能序列化的对象 =====
import jakarta.servlet.ServletRequest;                  // 请求对象，直接序列化会炸，必须跳过
import jakarta.servlet.ServletResponse;                 // 响应对象，同上
import jakarta.servlet.http.HttpServletRequest;         // 用来取 HTTP 方法、URI、IP

// ===== Lombok：减少样板代码 =====
import lombok.RequiredArgsConstructor;                  // 为所有 final 字段生成构造器（= 构造器注入）
import lombok.extern.slf4j.Slf4j;                       // 自动生成 private static final Logger log

// ===== AOP：切面编程的核心 API =====
import org.aspectj.lang.ProceedingJoinPoint;            // 环绕通知的连接点：能"暂停"目标方法，自己决定何时执行
import org.aspectj.lang.annotation.Around;              // 环绕通知，方法执行前后都能插逻辑
import org.aspectj.lang.annotation.Aspect;              // 声明"这是一个切面类"
import org.aspectj.lang.reflect.MethodSignature;         // 从连接点取出"方法签名"，从而拿到方法名、注解、参数类型

// ===== Spring 工具 =====
import org.springframework.core.annotation.AnnotatedElementUtils;        // 查找注解（支持 @AliasFor 等合并注解）
import org.springframework.stereotype.Component;                          // 让 Spring 扫描并创建这个 Bean
import org.springframework.util.StringUtils;                              // hasText：判断字符串非 null 且含非空白字符
import org.springframework.web.context.request.RequestContextHolder;      // 从当前线程取出请求上下文
import org.springframework.web.context.request.ServletRequestAttributes;  // 上下文的 Servlet 版本，能拿到 HttpServletRequest
import org.springframework.web.multipart.MultipartFile;                   // 上传文件，不能直接序列化（二进制），只记文件名和大小

// ===== JDK 集合 =====
import java.util.ArrayList;   // 用来收集 JSON 里的所有字段名（遍历时要复制一份，避免边遍历边改）
import java.util.List;        // 同上
import java.util.Locale;      // toLowerCase 指定 Locale，避免个别语言环境下的怪异行为
import java.util.Set;         // 敏感字段集合，查找是 O(1)

/**
 * 操作日志切面：拦截标注 @OperLog 的 Controller 方法，统一落库。
 * <p>
 * 三条硬约束：
 * 1) 日志写入失败只 warn，绝不影响主业务，也不覆盖原始异常；
 * 2) password / token 等敏感字段序列化后一律替换为 ***；
 * 3) 系统异常只记录异常类型，不把 SQL / 堆栈等细节写进日志表。
 */
@Slf4j                     // Lombok 生成 log 对象，等价于 private static final Logger log = ...
@Aspect                    // 告诉 Spring：这是一个切面类，里面的 @Around 会被解析成切点
@Component                 // 注册成 Bean（@Aspect 本身不会创建 Bean，必须配合 @Component）
@RequiredArgsConstructor   // 为下面 3 个 final 字段生成构造器 -> 构造器注入（比字段注入更好测）
public class OperLogAspect {

    /** 入参/返回值 JSON 的最大长度；超长就截断，防止一条日志把表撑爆 */
    private static final int MAX_PARAMS_LENGTH = 2000;
    /** 错误信息最大长度，同理 */
    private static final int MAX_ERROR_LENGTH = 500;
    /** 请求 URI 最大长度（正常 URI 不会很长，这里只是防御） */
    private static final int MAX_URI_LENGTH = 500;
    /** IP 最大长度：IPv6 最长 45 个字符，留点余量给 X-Forwarded-For 的异常值 */
    private static final int MAX_IP_LENGTH = 60;

    /**
     * 敏感字段黑名单。
     * 统一小写存放，比较时把字段名也转小写，这样 Password / PASSWORD / newPassword 都能命中。
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "confirmpassword",
            "token", "accesstoken", "refreshtoken", "secret", "authorization");

    /** 日志落库服务（写 t_oper_log） */
    private final OperLogService operLogService;
    /** 当前用户提供者：可能在未登录场景下抛异常，所以要 try/catch */
    private final CurrentUserProvider currentUserProvider;
    /** Jackson 总入口，Spring Boot 已自动配置好，直接注入即可 */
    private final ObjectMapper objectMapper;

    /**
     * 环绕通知：切点表达式 @annotation(OperLog) 表示"拦截所有标注了 @OperLog 的方法"。
     * 这里用 @Around 而不是 @Before/@AfterReturning，是为了同时拿到：
     * ① 返回值（记录结果）  ② 抛出的异常  ③ 耗时
     */
    @Around("@annotation(com.sched.annotation.OperLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("开始记录操作日志~~~~");
        long start = System.currentTimeMillis();   // 记录开始时间，用来算耗时
        Object result = null;                      // 存目标方法的返回值，供 finally 里记录
        Throwable error = null;                    // 存目标方法抛出的异常，finally 里据此判断成功/失败

        try {
            result = joinPoint.proceed();          // ★ 这一行才是真正执行业务方法（Controller 方法）
            return result;                         // 业务成功，原样返回结果给调用方
        } catch (Throwable t) {
            error = t;                             // 先把异常存下来，供 finally 记日志用
            throw t;                               // ★ 必须原样抛出：绝不能吞掉，否则前端拿不到错误
        } finally {
            // finally 保证：成功、失败都会记日志（哪怕 return 了、哪怕抛异常了）
            try {
                saveLog(joinPoint, result, error, System.currentTimeMillis() - start);   // 组装并落库
                log.info("记录操作日志成功~~~~");
            } catch (Exception e) {
                // 硬约束 1：日志失败不能影响业务。这里只 warn，绝不往外抛
                log.warn("操作日志写入失败，已忽略：{}", e.toString());
            }
        }
    }

    /**
     * 组装一条日志记录并落库。
     *
     * @param joinPoint 连接点，用来取方法签名、参数
     * @param result    业务返回值（失败时为 null）
     * @param error     业务异常（成功时为 null）
     * @param costMs    业务耗时（毫秒）
     */
    private void saveLog(ProceedingJoinPoint joinPoint, Object result, Throwable error, long costMs) {
        OperLog annotation = resolveAnnotation(joinPoint);   // 取出方法上的 @OperLog 实例，读 module/action 等属性
        if (annotation == null) {
            return;    // 理论上不会发生（切点已保证有注解），这里只是防御性判断
        }

        OperLogRecord record = new OperLogRecord();          // 准备一条日志实体
        record.setModule(annotation.module());               // 业务模块，如"个人中心"
        record.setAction(annotation.action());               // 操作动作，如"新增用户"
        record.setMethod(resolveMethodName(joinPoint));      // 记录是哪个类的哪个方法，如 UserController#addUser
        record.setCostMs(costMs);                            // 耗时
        record.setResultStatus(error == null ? OperResultStatus.SUCCESS.getCode() : OperResultStatus.FAIL.getCode());   // 用状态列方便统计失败率

        if (error != null) {                                 // 只有失败时才写错误信息
            record.setErrorMsg(truncate(describeError(error), MAX_ERROR_LENGTH));   // 描述 + 截断
        }

        fillRequest(record);    // 填 HTTP 方法、URI、IP（从当前请求上下文取）
        fillUser(record);       // 填当前登录用户 ID（未登录则留空）

        String payload = buildPayload(joinPoint.getArgs(), annotation, result, error);   // 组装入参/返回值的 JSON
        if (payload != null) {
            record.setRequestParams(payload);   // 有内容才写，空字符串就不占字段
        }

        operLogService.record(record);   // ★ 真正落库（走 MyBatis-Plus / JdbcTemplate，具体在 core 层）
    }

    /**
     * 从被拦截的方法上取出 @OperLog 注解实例。
     * 为什么还要反射取？因为切点只负责"匹配"，saveLog 里还需要读 module()、action() 这些属性。
     */
    private OperLog resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();   // 强转为方法签名，才有 getMethod()
        // findMergedAnnotation 会处理 @AliasFor 等"合并注解"场景，比直接 getAnnotation 更健壮
        return AnnotatedElementUtils.findMergedAnnotation(signature.getMethod(), OperLog.class);
    }

    /**
     * 拼出 "类名#方法名" 形式的方法标识，便于在日志里定位代码。
     * 用 getSimpleName()（不带包名）是为了日志简洁。
     */
    private String resolveMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "#" + signature.getName();
    }

    /**
     * 组装入参 / 返回值的 JSON 字符串。
     * 两个开关都关时不产生任何内容，返回 null，避免往库里写 "{}" 这种无意义数据。
     */
    private String buildPayload(Object[] args, OperLog annotation, Object result, Throwable error) {
        ObjectNode payload = objectMapper.createObjectNode();   // 创建 {"args":..., "result":...} 的容器

        if (annotation.recordParams()) {                        // 注解上 recordParams 默认 true
            payload.set("args", serializeArgs(args));           // 参数 -> JSON 数组（含脱敏）
        }
        if (annotation.recordResult() && error == null) {       // recordResult 默认 false；且只在成功时记返回值
            payload.set("result", safeToNode(result));
        }

        return payload.isEmpty() ? null : truncate(payload.toString(), MAX_PARAMS_LENGTH);   // 空则返回 null，超长截断
    }

    /**
     * 把方法参数数组转成 JSON 数组。
     * 这里做了三件"防雷"的事：过滤 Servlet 对象、特殊处理文件上传、递归脱敏。
     */
    private ArrayNode serializeArgs(Object[] args) {
        ArrayNode array = objectMapper.createArrayNode();   // 准备一个 JSON 数组
        if (args == null) {
            return array;                                   // 无参方法直接返回空数组
        }
        for (Object arg : args) {                           // 逐个参数处理
            // ① 跳过 null 和 Servlet 对象：HttpServletRequest 里有流、连接等，序列化必炸
            if (arg == null || arg instanceof ServletRequest || arg instanceof ServletResponse) {
                continue;
            }
            // ② 文件上传特殊处理：MultipartFile 是二进制，不能序列化，只记文件名和大小
            if (arg instanceof MultipartFile file) {
                ObjectNode node = array.addObject();        // 往数组里加一个对象元素，并返回该对象
                node.put("fileName", file.getOriginalFilename());
                node.put("size", file.getSize());
                continue;
            }
            // ③ 普通对象：安全地转成 JSON 节点
            array.add(safeToNode(arg));
        }
        mask(array);      // ★ 统一递归脱敏（password/token 等替换成 ***）
        return array;
    }

    /**
     * 对象 -> JSON 节点，序列化失败时不抛异常，而是返回一段占位文本。
     * 原因：日志功能不能因为"某个参数不可序列化"就把主业务搞挂。
     */
    private JsonNode safeToNode(Object value) {
        try {
            return objectMapper.valueToTree(value);   // 正常路径：对象转 JSON 树
        } catch (RuntimeException e) {
            // 兜底：某些对象（循环引用、无 getter 的特殊类型）可能转不了
            return TextNode.valueOf("[不可序列化：" + value.getClass().getSimpleName() + "]");
        }
    }

    /**
     * 递归遍历 JSON，把敏感字段的值替换成 ***。
     * 命中即替换、不再向下遍历 —— 因为一旦是 password，它的值没必要再分析。
     */
    private void mask(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            // 先把所有字段名复制出来：一边遍历 fieldNames 一边 put 可能抛 ConcurrentModificationException
            List<String> names = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(names::add);

            for (String name : names) {
                // 统一转小写再比较，这样 Password / NEWPASSWORD 也能命中黑名单
                if (SENSITIVE_KEYS.contains(name.toLowerCase(Locale.ROOT))) {
                    objectNode.put(name, "***");    // 命中敏感字段：值整体替换
                } else {
                    mask(objectNode.get(name));     // 不敏感：继续往下一层找（处理嵌套对象）
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            // 数组：对每个元素递归处理（比如 List<User> 里的 password）
            arrayNode.forEach(this::mask);
        }
        // 其它类型（TextNode/NumberNode/NullNode）不需要处理
    }

    /**
     * 从当前线程绑定的请求上下文中取出 HTTP 信息。
     * 注意：RequestContextHolder 是 ThreadLocal 实现，异步线程/定时任务里取不到，所以要先判 null。
     */
    private void fillRequest(OperLogRecord record) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;    // 非 Web 线程（如定时任务、异步线程）调用时，没有请求上下文，跳过即可
        }
        HttpServletRequest request = attributes.getRequest();
        record.setRequestMethod(request.getMethod());                             // GET / POST ...
        record.setRequestUri(truncate(request.getRequestURI(), MAX_URI_LENGTH));  // 请求路径
        record.setIp(resolveIp(request));                                         // 客户端 IP
    }

    /**
     * 解析客户端 IP，优先级：X-Forwarded-For -> X-Real-IP -> remoteAddr。
     * 注意：前两个 header 可以被客户端伪造，只可用于审计展示，绝不能用于鉴权。
     */
    private String resolveIp(HttpServletRequest request) {
        String ip = null;

        String forwarded = request.getHeader("X-Forwarded-For");   // 经过代理时，真实 IP 在最左边
        if (StringUtils.hasText(forwarded)) {
            ip = forwarded.split(",")[0].trim();                  // 取第一个（最原始的客户端 IP）
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getHeader("X-Real-IP");                  // Nginx 常用这个 header
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getRemoteAddr();                         // 直连场景：TCP 层的对端地址
        }
        return truncate(ip, MAX_IP_LENGTH);                       // 防御性截断，防止超长 header 写库失败
    }

    /**
     * 填当前登录用户 ID。
     * 未登录是正常场景（比如登录接口本身就带 @OperLog），所以异常只 debug，不写 error。
     */
    private void fillUser(OperLogRecord record) {
        try {
            record.setUserId(currentUserProvider.currentUserId());
        } catch (RuntimeException e) {
            // 未登录属于正常场景：日志仍要记录，只是不关联用户
            log.debug("操作日志未识别到当前用户：{}", e.getMessage());
        }
    }

    /**
     * 把异常转成"可读且安全"的描述。
     * 硬约束 3：系统异常只记类型，不记 message（message 里可能有 SQL、表名、连接串）。
     */
    private String describeError(Throwable error) {
        if (error instanceof BizException bizException) {
            // 业务异常是"我们自己抛的"，message 是写给用户看的，可以安全记录
            return "业务异常[" + bizException.getCode() + "] " + bizException.getMessage();
        }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            // 参数/状态类异常通常是开发自己写的提示，信息量足够且不含敏感数据
            return error.getClass().getSimpleName() + ": " + error.getMessage();
        }
        // 其它（SQLException、NullPointerException 等）一律只留类型名
        return "系统异常 " + error.getClass().getSimpleName();
    }

    /**
     * 安全截断：null 原样返回，超长则截到 maxLength。
     * 目的是防止某个超长参数/堆栈把数据库字段撑爆（varchar 超长会直接抛异常）。
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
