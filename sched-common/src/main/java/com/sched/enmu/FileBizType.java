package com.sched.enmu;

import lombok.Getter;

/**
 * 文件业务类型，对应 t_sched_file.biz_type。
 * <p>
 * 同时作为上传时"业务类型白名单"，防止前端传任意值来创建目录。
 */
@Getter
public enum FileBizType {

    /** 用户头像 */
    AVATAR("AVATAR", "用户头像"),

    /** 普通附件 */
    ATTACHMENT("ATTACHMENT", "普通附件"),

    /** 导出文件 */
    EXPORT("EXPORT", "导出文件");

    private final String code;
    private final String desc;

    FileBizType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static FileBizType ofCode(String code) {
        for (FileBizType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的业务类型：" + code);
    }
}
