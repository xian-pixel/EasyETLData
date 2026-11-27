-- ============================================================
-- 同步主线：规则引擎三张表（RuleEngine 读取）
--
--   - t_sync_rule              规则头：priority 数字小的先执行
--   - t_sync_rule_condition    条件：同一 area_num 内 AND，不同 area_num 之间 OR
--   - t_sync_rule_assignment   动作：命中后给目标列赋值（EXPR 表达式 / TEXT 常量）
--
-- 执行方式：命中条件后由 QLExpress 计算表达式，结果写入目标列；
--           所有查询都带 enable_status='1' 过滤，停用的规则/动作不参与计算。
-- ============================================================

USE sched_platform;

-- ------------------------------------------------------------
-- 1. 规则头
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_rule
(
    rule_id       VARCHAR(32)  NOT NULL COMMENT '规则ID',
    task_id       VARCHAR(32)  NOT NULL COMMENT '所属任务ID',
    rule_name     VARCHAR(128)          DEFAULT NULL COMMENT '规则名称',
    priority      INT          NOT NULL DEFAULT 0 COMMENT '优先级，数字小的先执行',
    enable_status CHAR(1)      NOT NULL DEFAULT '1' COMMENT '0 停用，1 启用',
    PRIMARY KEY (rule_id),
    KEY idx_task (task_id, priority)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='规则';

-- ------------------------------------------------------------
-- 2. 规则条件（同 area_num 内 AND，不同 area_num 之间 OR）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_rule_condition
(
    condition_id  VARCHAR(32)  NOT NULL COMMENT '条件ID',
    rule_id       VARCHAR(32)  NOT NULL COMMENT '所属规则ID',
    area_num      INT          NOT NULL DEFAULT 1 COMMENT '条件分组号',
    property_name VARCHAR(64)  NOT NULL COMMENT '列名（驼峰）',
    operator      VARCHAR(32)  NOT NULL COMMENT '比较符，取值见手册 §7.3',
    value         VARCHAR(512)          DEFAULT NULL COMMENT '比较值',
    value_type    VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT=字面量 / COLUMN=取某列的值',
    sort_no       INT          NOT NULL DEFAULT 0 COMMENT '组内顺序',
    PRIMARY KEY (condition_id),
    KEY idx_rule (rule_id, area_num, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='规则条件';

-- ------------------------------------------------------------
-- 3. 规则动作（命中后给目标列赋值）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_rule_assignment
(
    assignment_id VARCHAR(32)  NOT NULL COMMENT '动作ID',
    rule_id       VARCHAR(32)  NOT NULL COMMENT '所属规则ID',
    target_column VARCHAR(64)  NOT NULL COMMENT '目标列',
    value_type    VARCHAR(16)  NOT NULL COMMENT 'EXPR=表达式 / TEXT=常量',
    value         VARCHAR(512)          DEFAULT NULL COMMENT '表达式或常量值',
    sort_no       INT          NOT NULL DEFAULT 0 COMMENT '执行顺序',
    enable_status CHAR(1)      NOT NULL DEFAULT '1' COMMENT '0 停用，1 启用；实现时必须校验，否则禁用的动作也会执行',
    PRIMARY KEY (assignment_id),
    KEY idx_rule (rule_id, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='规则动作';
