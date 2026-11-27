package com.sched.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志实体，对应 t_sched_oper_log。
 * 审计数据只追加不修改，因此没有 deleted 列；
 * username 为写入时的快照，便于用户改名/删除后仍可追溯。
 */
@Data
@TableName("t_sched_oper_log")
public class OperLogRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志ID（雪花）
     */
    @TableId(value = "log_id", type = IdType.ASSIGN_ID)
    private Long logId;

    /**
     * 操作人ID，可能为空（未识别到登录用户）
     */
    private Long userId;

    /**
     * 操作人登录名快照
     */
    private String username;

    /**
     * 业务模块，如：个人中心
     */
    private String module;

    /**
     * 操作动作，如：新增用户
     */
    private String action;

    /**
     * 目标方法：简单类名#方法名
     */
    private String method;

    /**
     * HTTP 方法
     */
    private String requestMethod;

    /**
     * 请求路径
     */
    private String requestUri;

    /**
     * 脱敏后的入参 JSON，超长截断
     */
    private String requestParams;

    /**
     * 执行结果：1 成功，0 失败
     */
    private Integer resultStatus;

    /**
     * 失败原因，仅记录业务异常文案或异常类型
     */
    private String errorMsg;

    /**
     * 客户端IP
     */
    private String ip;

    /**
     * 耗时（毫秒）
     */
    private Long costMs;

    /**
     * 记录时间，由数据库默认值维护
     */
    private LocalDateTime createTime;
}
