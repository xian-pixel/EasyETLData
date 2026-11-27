package com.sched.enmu;

import lombok.Getter;

/**
 * 错误明细类型，对应 t_sync_error_detail.error_type。
 */
@Getter
public enum ErrorType {

    /** 规则执行异常 */
    RULE_EXECUTE_ERROR("RULE_EXECUTE_ERROR", "规则执行异常"),

    /** 批量写入 SQL 异常 */
    SQL_ERROR("SQL_ERROR", "SQL 执行异常"),

    /** 数据转换/映射异常 */
    MAPPING_ERROR("MAPPING_ERROR", "字段映射异常");

    private final String code;
    private final String desc;

    ErrorType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ErrorType ofCode(String code) {
        for (ErrorType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的错误类型：" + code);
    }
}
