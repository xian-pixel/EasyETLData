-- ============================================================
-- 同步主线：任务定义 / 字段映射 / 表列白名单
--
-- 配置驱动的基础三张表：
--   - t_sync_task              一个任务 = 源表 -> 目标表 + 分片数 + 批次大小
--   - t_sync_field_mapping     任务内逐列映射（COLUMN 取源列 / TEXT 写常量）
--   - t_sync_table_whitelist   源表与目标表各自允许出现的列名，SqlBuilder 拼 SQL 前必须查
--
-- 三张表缺任何一张，ConfigDrivenRunner 都会在加载配置时直接失败。
-- ============================================================

USE sched_platform;

-- ------------------------------------------------------------
-- 1. 任务定义
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_task
(
    task_id        VARCHAR(32)  NOT NULL COMMENT '任务ID',
    task_code      VARCHAR(64)  NOT NULL COMMENT '任务编码，唯一',
    task_name      VARCHAR(128) NOT NULL COMMENT '任务名称',
    source_table   VARCHAR(64)  NOT NULL COMMENT '源表名',
    target_table   VARCHAR(64)  NOT NULL COMMENT '目标表名',
    sync_type      VARCHAR(16)  NOT NULL DEFAULT 'FULL' COMMENT '同步类型：FULL/INCREMENTAL（当前只落地 FULL）',
    batch_size     INT          NOT NULL DEFAULT 1000 COMMENT '分批读取/写入的批次大小',
    shard_total    INT          NOT NULL DEFAULT 1 COMMENT '分片数，1=不分片；>1 时按 MOD(CRC32(分片列), 分片数) 切片',
    block_strategy VARCHAR(16)  NOT NULL DEFAULT 'SERIAL' COMMENT '阻塞策略',
    cron_expr      VARCHAR(64)           DEFAULT NULL COMMENT '调度表达式（由外部调度器使用）',
    job_param      VARCHAR(1000)         DEFAULT NULL COMMENT '业务参数 JSON',
    enable_status  CHAR(1)      NOT NULL DEFAULT '1' COMMENT '0 停用，1 启用',
    del_flag       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未删除，1 已删除',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_task_code (task_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='同步任务定义';

-- ------------------------------------------------------------
-- 2. 字段映射
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_field_mapping
(
    mapping_id    VARCHAR(32)  NOT NULL COMMENT '映射ID',
    task_id       VARCHAR(32)  NOT NULL COMMENT '所属任务ID',
    source_column VARCHAR(64)           DEFAULT NULL COMMENT '源列；value_type=TEXT 时可为空',
    target_column VARCHAR(64)  NOT NULL COMMENT '目标列',
    value_type    VARCHAR(16)  NOT NULL DEFAULT 'COLUMN' COMMENT 'COLUMN=取源列 / TEXT=写常量',
    value         VARCHAR(512)          DEFAULT NULL COMMENT 'value_type=TEXT 时的常量值',
    field_seq     INT          NOT NULL DEFAULT 0 COMMENT '列顺序',
    enable_status CHAR(1)      NOT NULL DEFAULT '1' COMMENT '0 停用，1 启用',
    PRIMARY KEY (mapping_id),
    KEY idx_task (task_id, field_seq)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='字段映射';

-- ------------------------------------------------------------
-- 3. 表列白名单（动态表名/列名必须查这里）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_table_whitelist
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    table_name    VARCHAR(64) NOT NULL COMMENT '表名',
    table_role    VARCHAR(16) NOT NULL COMMENT 'SOURCE=源表 / TARGET=目标表',
    column_names  TEXT        NOT NULL COMMENT '允许的列名，逗号分隔',
    enable_status CHAR(1)     NOT NULL DEFAULT '1' COMMENT '0 停用，1 启用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_table_role (table_name, table_role)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='表列白名单';
