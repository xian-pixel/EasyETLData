package com.sched.enmu;

import lombok.Getter;

/**
 * 批次执行状态，对应 t_sync_batch_log.status。
 * <p>
 * 状态流转：RUNNING -> SUCCESS / FAILED；故障转移会把超时的 RUNNING 直接改成 FAILED。
 */
@Getter
public enum BatchStatus {

    /** 运行中（刚开始写入，尚未结束） */
    RUNNING("RUNNING", "运行中"),

    /** 成功 */
    SUCCESS("SUCCESS", "成功"),

    /** 失败（含故障转移判定为僵尸批次） */
    FAILED("FAILED", "失败");

    private final String code;
    private final String desc;

    BatchStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static BatchStatus ofCode(String code) {
        for (BatchStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的批次状态码：" + code);
    }
}
