package com.sched.core.log;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 记录日志服务
 * 详情：每次开始同步之前记录一条日志，同步结束之后记录一条日志
 *
 * @auther 邢正阳
 */
@Service
public class BatchLogService {
    //首先注入jdbcTemplate
    private final JdbcTemplate jdbcTemplate;

    public BatchLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 开始记录日志
     */
    public String start(String taskId, int shard_index, int shard_total) {
        String batchId = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("INSERT INTO t_sync_batch_log" +
                        "(batch_id,task_id,shard_index,shard_total,status,start_time) " +
                        "VALUES(?,?,?,?,'RUNNING',NOW())",
                batchId, taskId, shard_index, shard_total);
        return batchId;
    }

    /**
     * 结束一批同步并回写日志；无论成功还是失败都必须调用（建议放在 finally 块里）。
     *
     * @param batchId      批次 ID，由 {@link #start(String, int, int)} 生成
     * @param shardIndex   分片序号，从 0 开始；与 batchId 联合唯一定位一行日志
     * @param status       批次最终状态，如 SUCCESS / FAILED
     * @param successCount 本批次成功同步的行数
     * @param failCount    本批次失败的行数
     * @param errorMsg     失败原因，可为 {@code null}；超过 1000 字符会被截断
     * @param costMs       本分片耗时，单位毫秒
     */
    public void finish(String batchId, int shardIndex, String status,
                       long successCount, long failCount, String errorMsg, long costMs) {
        jdbcTemplate.update(
                "UPDATE t_sync_batch_log SET end_time = NOW(), duration_ms = ?,"
                        + " status = ?, success_count = ?, fail_count = ?, error_msg = ?"
                        + " WHERE batch_id = ? AND shard_index = ?",
                costMs, status, successCount, failCount,
                errorMsg == null ? null : errorMsg.substring(0, Math.min(errorMsg.length(), 1000)),
                batchId, shardIndex);
    }
}
