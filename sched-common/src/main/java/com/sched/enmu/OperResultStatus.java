package com.sched.enmu;

import lombok.Getter;

/**
 * 操作日志执行结果，对应 t_oper_log.result_status。
 */
@Getter
public enum OperResultStatus {

    /** 业务执行成功 */
    SUCCESS(1, "成功"),

    /** 业务执行失败（抛出了异常） */
    FAIL(0, "失败");

    private final int code;
    private final String desc;

    OperResultStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OperResultStatus ofCode(Integer code) {
        for (OperResultStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的操作结果码：" + code);
    }
}
