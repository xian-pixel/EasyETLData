package com.sched.core.model;

/**
 * 任务配置（从 t_sync_task 读出来的一行）
 */
public record SyncTaskConfig(
        String taskId,
        String taskCode,
        String sourceTable,
        String targetTable,
        String syncType,
        int batchSize,
        String bizKey,
        int shardTotal,
        String blockStrategy) {
}