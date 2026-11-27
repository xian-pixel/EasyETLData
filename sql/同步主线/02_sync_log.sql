-- ============================================================
-- 同步主线：运行记录（批次日志 / 失败明细）
--
--   - t_sync_batch_log     每个任务每次触发生成一批，每个分片一行，主键 (batch_id, shard_index)
--   - t_sync_error_detail  具体失败明细，按 (batch_id, shard_index) 关联批次
--
-- 状态机：RUNNING -> SUCCESS / FAILED
-- 长期停留在 RUNNING 的行就是"僵尸批次"，由 FailoverJob 扫描并转移重跑。
-- ============================================================

USE sched_platform;

-- ------------------------------------------------------------
-- 1. 批次日志
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_batch_log
(
    batch_id      VARCHAR(40)   NOT NULL COMMENT '批次号：一次任务触发生成一个，同批次的所有分片共享',
    task_id       VARCHAR(32)   NOT NULL COMMENT '任务ID，关联 t_sync_task.task_id',
    shard_index   INT           NOT NULL DEFAULT 0 COMMENT '分片编号，从 0 开始，取值范围 [0, shard_total)',
    shard_total   INT           NOT NULL DEFAULT 1 COMMENT '本次执行的总分片数，1 表示未分片',
    start_time    DATETIME               DEFAULT NULL COMMENT '该分片开始时间',
    end_time      DATETIME               DEFAULT NULL COMMENT '该分片结束时间；status=RUNNING 时为空',
    duration_ms   BIGINT                 DEFAULT 0 COMMENT '该分片耗时(毫秒)，等于 end_time - start_time，冗余存储便于统计',
    status        VARCHAR(16)   NOT NULL DEFAULT 'RUNNING' COMMENT '分片状态：RUNNING=执行中 / SUCCESS=成功 / FAILED=失败',
    success_count BIGINT        NOT NULL DEFAULT 0 COMMENT '该分片成功写入行数',
    fail_count    BIGINT        NOT NULL DEFAULT 0 COMMENT '该分片失败行数，大于 0 时 status 通常为 FAILED',
    error_msg     VARCHAR(1000)          DEFAULT NULL COMMENT '失败摘要（异常类名+简要信息），完整明细见 t_sync_error_detail',
    PRIMARY KEY (batch_id, shard_index),
    KEY idx_task_time (task_id, start_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='批次日志';

-- ------------------------------------------------------------
-- 2. 失败明细
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_error_detail
(
    error_id    VARCHAR(32)   NOT NULL COMMENT '错误ID',
    batch_id    VARCHAR(40)   NOT NULL COMMENT '批次号，关联 t_sync_batch_log.batch_id',
    shard_index INT           NOT NULL DEFAULT 0 COMMENT '分片编号',
    error_type  VARCHAR(64)   NOT NULL COMMENT '错误类型，如 SQL_ERROR / RULE_EXECUTE_ERROR',
    error_msg   VARCHAR(1000)          DEFAULT NULL COMMENT '错误信息',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (error_id),
    KEY idx_batch (batch_id, shard_index)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='失败明细';
