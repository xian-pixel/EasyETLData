package com.sched.enmu;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一返回状态码枚举
 * 号段规划：
 * 0     成功
 * 1xxx  参数/请求错误
 * 2xxx  登录/权限
 * 3xxx  调度任务业务
 * 4xxx  中间件/系统依赖
 * 500   未知异常兜底
 */
@Getter
@AllArgsConstructor
public enum ResultValue {

    // ==================== 成功 ====================
    SUCCESS(0, "操作成功"),

    // ==================== 1xxx 参数/请求错误 ====================
    PARAM_ERROR(1001, "参数错误"),
    PARAM_MISSING(1002, "缺少必填参数"),
    PARAM_FORMAT_ERROR(1003, "参数格式不正确"),
    REQUEST_METHOD_NOT_SUPPORTED(1004, "请求方式不支持"),
    DATA_NOT_FOUND(1005, "数据不存在"),
    DATA_DUPLICATED(1006, "数据已存在"),
    DATA_EXPIRED(1007, "数据已过期"),

    // ==================== 2xxx 登录/权限 ====================
    NOT_LOGGED_IN(2001, "用户未登录"),
    TOKEN_INVALID(2002, "登录凭证已失效"),
    TOKEN_EXPIRED(2003, "登录凭证已过期"),
    USERNAME_OR_PASSWORD_ERROR(2004, "用户名或密码错误"),
    ACCOUNT_DISABLED(2005, "账号已被禁用"),
    NO_PERMISSION(2006, "没有操作权限"),
    CAPTCHA_ERROR(2007, "验证码错误"),

    // ==================== 3xxx 调度任务业务 ====================
    TASK_NOT_FOUND(3001, "调度任务不存在"),
    TASK_ALREADY_RUNNING(3002, "任务正在运行中"),
    TASK_ALREADY_STOPPED(3003, "任务已处于停止状态"),
    TASK_TRIGGER_FAILED(3004, "任务触发失败"),
    TASK_CONFIG_INVALID(3005, "任务配置不合法"),
    CRON_EXPRESSION_INVALID(3006, "Cron 表达式不合法"),
    TASK_DEPENDENCY_CYCLE(3007, "任务依赖存在环"),
    NODE_NOT_AVAILABLE(3008, "无可用的执行节点"),
    SYNC_SOURCE_EMPTY(3009, "同步源数据为空"),
    SYNC_FAILED(3010, "数据同步失败"),

    // ==================== 4xxx 中间件/系统依赖 ====================
    DB_ERROR(4001, "数据库操作异常"),
    REDIS_ERROR(4002, "缓存操作异常"),
    MQ_SEND_FAILED(4003, "消息发送失败"),
    THIRD_PARTY_ERROR(4004, "第三方接口调用失败"),
    FILE_UPLOAD_FAILED(4005, "文件上传失败"),

    // ==================== 500 兜底 ====================
    SYSTEM_ERROR(500, "系统繁忙，请稍后再试");

    /**
     * 状态码
     */
    private final Integer code;
    /**
     * 提示信息
     */
    private final String msg;
}

