package com.sched.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Controller 方法上，由 OperLogAspect 统一记录。
 * <p>
 * 只用于写操作（新增/修改/删除/上传/下载），查询接口不要标注，避免日志噪音。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /**
     * 业务模块，如：个人中心
     */
    String module();

    /**
     * 操作动作，如：新增用户
     */
    String action();

    /**
     * 是否记录入参。默认记录，密码类字段会被自动脱敏为 ***。
     */
    boolean recordParams() default true;

    /**
     * 是否记录返回值。默认不记录，避免把用户资料、文件内容等写入日志。
     */
    boolean recordResult() default false;
}
