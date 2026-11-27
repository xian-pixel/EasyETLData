-- ============================================================
-- 同步主线：执行器与选主（M9 容灾）
--
--   - t_sync_executor  执行器注册表：心跳 upsert，超时未心跳视为失联
--   - t_sync_lock      选主租约锁：INSERT IGNORE 抢锁 + expire_at 到期可被抢占
--
-- 设计取舍：不使用 ZooKeeper / Redis，靠 MySQL 单表实现"谁有权做故障转移"，
--           抢锁与续约是同一条 UPDATE，不需要额外的续约任务。
-- 时间必须由数据库 NOW() 生成，多机时钟漂移会导致双主。
-- ============================================================

USE sched_platform;

-- ------------------------------------------------------------
-- 1. 执行器注册表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_executor
(
    executor_id    VARCHAR(32) NOT NULL COMMENT '执行器ID',
    app_name       VARCHAR(64) NOT NULL COMMENT '应用名',
    address        VARCHAR(64) NOT NULL COMMENT 'ip:port',
    running_shards INT         NOT NULL DEFAULT 0 COMMENT '当前运行中的分片数',
    last_heartbeat DATETIME             DEFAULT NULL COMMENT '最近心跳时间',
    enable_status  CHAR(1)     NOT NULL DEFAULT '1' COMMENT '0 停用，1 启用',
    PRIMARY KEY (executor_id),
    UNIQUE KEY uk_app_addr (app_name, address),
    KEY idx_heartbeat (last_heartbeat)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='执行器注册表';

-- ------------------------------------------------------------
-- 2. 调度中心选主租约锁
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_lock
(
    lock_name VARCHAR(64) NOT NULL COMMENT '锁名，本版只有 sched:master',
    owner     VARCHAR(64) NOT NULL COMMENT '持有者：appName@随机串，便于排查是谁持锁',
    expire_at DATETIME    NOT NULL COMMENT '租约到期时间，过了这个点任何实例都能抢',
    PRIMARY KEY (lock_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='调度中心选主租约锁';
