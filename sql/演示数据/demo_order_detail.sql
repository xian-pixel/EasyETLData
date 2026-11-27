-- ============================================================
-- 演示数据：同步链路的源表与目标表（仅本地联调使用，生产不要创建）
--
--   - src_order_detail  源表，金额故意用 VARCHAR 模拟真实的类型陷阱
--   - ods_order_detail  目标表，net_amount 由规则引擎计算（amount - tax_amount）
--
-- 配合 /api/dev/run-once、/api/dev/run-once-config、/api/dev/run-shard 使用；
-- 目标表的列必须在 t_sync_table_whitelist 里登记，否则 ConfigDrivenRunner 拒绝执行。
-- ============================================================

USE sched_platform;

-- ------------------------------------------------------------
-- 1. 演示源表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS src_order_detail
(
    order_id    VARCHAR(32) NOT NULL COMMENT '订单号',
    line_no     INT         NOT NULL COMMENT '行号',
    customer    VARCHAR(64)          DEFAULT NULL COMMENT '客户名',
    amount      VARCHAR(32)          DEFAULT NULL COMMENT '金额（字符串，模拟类型陷阱）',
    tax_amount  VARCHAR(32)          DEFAULT NULL COMMENT '税额（字符串）',
    update_time DATETIME             DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (order_id, line_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='演示源表-订单明细';

-- ------------------------------------------------------------
-- 2. 演示目标表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ods_order_detail
(
    order_id    VARCHAR(32) NOT NULL COMMENT '订单号',
    line_no     INT         NOT NULL COMMENT '行号',
    customer    VARCHAR(64)          DEFAULT NULL COMMENT '客户名',
    amount      VARCHAR(32)          DEFAULT NULL COMMENT '金额',
    tax_amount  VARCHAR(32)          DEFAULT NULL COMMENT '税额',
    net_amount  VARCHAR(32)          DEFAULT NULL COMMENT '规则计算：amount - tax_amount',
    data_source VARCHAR(16)          DEFAULT NULL COMMENT '数据来源标识',
    create_time DATETIME             DEFAULT NULL COMMENT '写入时间',
    PRIMARY KEY (order_id, line_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='演示目标表-订单明细';
