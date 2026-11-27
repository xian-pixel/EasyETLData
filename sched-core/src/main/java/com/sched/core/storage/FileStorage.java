package com.sched.core.storage;

import java.io.InputStream;

/**
 * 文件存储抽象：本地磁盘 / MinIO / OSS 只需提供不同实现，业务层不感知。
 * objectKey 一律由实现类生成，禁止把用户传入的文件名拼进路径。
 */
public interface FileStorage {

    /**
     * 保存文件，返回服务端生成的 objectKey（相对路径，不含绝对目录）
     *
     * @param in      文件流，由调用方负责关闭
     * @param bizType 业务类型，作为一级目录，如 avatar / attachment
     * @param ext     小写扩展名，不含点，可为空
     */
    String store(InputStream in, String bizType, String ext);

    /**
     * 按 objectKey 打开文件流，调用方负责关闭
     */
    InputStream load(String objectKey);

    /**
     * 删除文件；不存在时静默返回
     */
    void delete(String objectKey);
}
