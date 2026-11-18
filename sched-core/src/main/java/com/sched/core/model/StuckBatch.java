//sched-core/src/main/java/com/sched/core/model/StuckBatch.java
package com.sched.core.model;

/**
 * 僵尸批次：一个批次行 + 它属于哪个任务
 */
public record StuckBatch(String batchId,
                         String taskCode,
                         int shardIndex,
                         int shardTotal) {
}