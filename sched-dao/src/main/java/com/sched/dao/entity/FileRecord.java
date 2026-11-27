package com.sched.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件元数据实体，对应 t_sched_file。
 * 只存元信息和 objectKey，绝不存绝对路径或可直接访问的 URL。
 */
@Data
@TableName("t_sched_file")
public class FileRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件ID（雪花）
     */
    @TableId(value = "file_id", type = IdType.ASSIGN_ID)
    private Long fileId;

    /**
     * 业务类型：AVATAR / ATTACHMENT / EXPORT
     */
    private String bizType;

    /**
     * 关联业务ID，如头像对应用户ID
     */
    private Long bizId;

    /**
     * 上传人ID，查询与下载鉴权必须以此过滤
     */
    private Long uploaderId;

    /**
     * 原始文件名，仅用于展示与下载时的 Content-Disposition
     */
    private String originalName;

    /**
     * 小写扩展名，不含点
     */
    private String ext;

    /**
     * MIME 类型，可为空
     */
    private String contentType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 存储类型：LOCAL / MINIO / OSS
     */
    private String storageType;

    /**
     * 存储相对路径或对象 Key，由服务端生成，如 avatar/2026/11/26/xxx.png
     */
    private String objectKey;

    /**
     * 文件 MD5，用于秒传/去重，可为空
     */
    private String md5;

    /**
     * 状态：1 有效，0 失效
     */
    private Integer status;

    /**
     * 创建时间，由数据库默认值维护
     */
    private LocalDateTime createTime;

    /**
     * 逻辑删除标记：0 未删除，非 0 为删除时间戳（同 User，不用 @TableLogic）
     */
    private Long deleted;
}
