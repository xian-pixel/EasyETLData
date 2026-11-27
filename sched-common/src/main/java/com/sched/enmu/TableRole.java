package com.sched.enmu;

import lombok.Getter;

/**
 * 表角色，对应 t_sync_table_whitelist.table_role。
 * <p>
 * 源表和白名单分开配置，是为了让"读哪张表"和"写哪张表"各自独立审查。
 */
@Getter
public enum TableRole {

    /** 源表（读取侧） */
    SOURCE("SOURCE", "源表"),

    /** 目标表（写入侧） */
    TARGET("TARGET", "目标表");

    private final String code;
    private final String desc;

    TableRole(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TableRole ofCode(String code) {
        for (TableRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的表角色：" + code);
    }
}
