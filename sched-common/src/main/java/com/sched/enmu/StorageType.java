package com.sched.enmu;

import lombok.Getter;

/**
 * 文件存储方式，对应 t_sched_file.storage_type。
 * <p>
 * 目前只实现本地磁盘；接入 OSS/MinIO 时在这里加枚举，并在 FileStorage 增加实现。
 */
@Getter
public enum StorageType {

    /** 本地磁盘 */
    LOCAL("LOCAL", "本地磁盘"),

    /** 对象存储（预留） */
    OSS("OSS", "对象存储");

    private final String code;
    private final String desc;

    StorageType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static StorageType ofCode(String code) {
        for (StorageType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的存储类型：" + code);
    }
}
