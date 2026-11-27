package com.sched.enmu;

import lombok.Getter;

/**
 * 字段取值类型，对应 t_sync_field_mapping.value_type 与 t_sync_rule_assignment.value_type。
 */
@Getter
public enum ValueType {

    /** 源表列：值来自源表查询结果 */
    COLUMN("COLUMN", "源表列"),

    /** 常量：值直接取配置里的 value */
    TEXT("TEXT", "常量"),

    /** 规则计算列：值由规则引擎算出，源表没有这一列 */
    RULE("RULE", "规则计算列"),

    /** 表达式：value 是一段需要执行的表达式（如 减(amount, taxAmount)） */
    EXPR("EXPR", "表达式");

    private final String code;
    private final String desc;

    ValueType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ValueType ofCode(String code) {
        for (ValueType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的取值类型：" + code);
    }

    /**
     * 大小写不敏感地判断码值是否等于本枚举，避免调用方自己写 equals 时的空指针与大小写问题。
     */
    public boolean is(String code) {
        return this.code.equalsIgnoreCase(code);
    }
}
