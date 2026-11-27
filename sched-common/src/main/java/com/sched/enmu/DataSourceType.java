package com.sched.enmu;

import lombok.Getter;

/**
 * 目标表数据来源标记，写入 ods_* 表的 data_source 列。
 * <p>
 * 用来区分"从源库同步过来的数据"和"人工补录/其他渠道写入的数据"。
 */
@Getter
public enum DataSourceType {

    /** 源库同步 */
    SRC("SRC", "源库同步"),

    /** 人工补录 */
    MANUAL("MANUAL", "人工补录");

    private final String code;
    private final String desc;

    DataSourceType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static DataSourceType ofCode(String code) {
        for (DataSourceType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的数据来源：" + code);
    }
}
