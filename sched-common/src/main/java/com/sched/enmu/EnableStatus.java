package com.sched.enmu;

import lombok.Getter;

/**
 * 通用启用/禁用状态。
 * <p>
 * 同时覆盖两种列类型：
 * <ul>
 *     <li>char/varchar 列（如 t_sync_executor.enable_status）：用 {@link #getCode()}</li>
 *     <li>int 列（如 t_sched_user.status、t_sched_file.status）：用 {@link #getIntCode()}</li>
 * </ul>
 */
@Getter
public enum EnableStatus {

    /** 启用 */
    ENABLED("1", 1, "启用"),

    /** 禁用 */
    DISABLED("0", 0, "禁用");

    /** 字符串码：对应 char/varchar 列，也用于 SQL 参数绑定 */
    private final String code;

    /** 数字码：对应 int 列 */
    private final int intCode;

    /** 中文描述：用于日志与前端展示 */
    private final String desc;

    EnableStatus(String code, int intCode, String desc) {
        this.code = code;
        this.intCode = intCode;
        this.desc = desc;
    }

    /**
     * 按字符串码反查枚举，用于从数据库读出来的值。
     *
     * @param code 数据库中的码值
     * @return 对应枚举
     * @throws IllegalArgumentException 码值非法
     */
    public static EnableStatus ofCode(String code) {
        for (EnableStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的启用状态码：" + code);
    }

    public boolean isEnabled() {
        return this == ENABLED;
    }
}
