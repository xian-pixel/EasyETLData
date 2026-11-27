package com.sched.core.vo.file;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件对外视图：不返回 objectKey、绝对路径或存储根目录。
 */
@Data
public class FileVO {

    private Long fileId;

    private String bizType;

    private String originalName;

    private String ext;

    private String contentType;

    private Long fileSize;

    private LocalDateTime createTime;
}
