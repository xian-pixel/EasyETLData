package com.sched.core.dto;

import com.sched.dao.entity.FileRecord;

import java.io.InputStream;

/**
 * 下载/预览用的文件内容载体：元数据 + 流，流由 Controller 负责关闭。
 */
public record FileContent(FileRecord meta, InputStream stream) {
}
