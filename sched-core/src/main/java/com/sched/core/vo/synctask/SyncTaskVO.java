package com.sched.core.vo.synctask;

import lombok.Data;

/**
 * 同步任务详情／列表返回对象。
 * 刻意不包含 del_flag（逻辑删除标记属于内部实现，不暴露给前端）。
 */
@Data
public class SyncTaskVO {

    private String taskId;

    private String taskCode;

    private String sourceTable;

    private String targetTable;

    private String syncType;

    private Integer batchSize;

    private String bizKey;

    private Integer shardTotal;

    private String blockStrategy;

    /** 启用状态：'1' 启用，'0' 停用 */
    private String enableStatus;
}
