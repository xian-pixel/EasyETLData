-- ============================================================
-- 操作日志建表脚本（MySQL 8.0+）
--
-- 设计约定：
-- 1) 审计表只追加、不修改，因此不设 deleted 逻辑删除列；
-- 2) user_id / username 均冗余存储：用户改名或删除后，历史日志仍可追溯到当时身份；
-- 3) request_params 只存脱敏后的 JSON 文本（password 类字段一律替换为 ***）；
-- 4) 主键由 MyBatis-Plus ASSIGN_ID（雪花算法）生成，类型 BIGINT；
-- 5) 表名/列名与实体注解完全一致，不要自行改名。
-- ============================================================

USE sched_platform;

-- ------------------------------------------------------------
-- 操作日志表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sched_oper_log
(
    log_id         BIGINT       NOT NULL COMMENT '日志ID（雪花）',
    user_id        BIGINT                DEFAULT NULL COMMENT '操作人ID，NULL 表示未识别到登录用户',
    username       VARCHAR(64)           DEFAULT NULL COMMENT '操作人登录名（冗余快照）',
    module         VARCHAR(64)  NOT NULL COMMENT '业务模块，如：个人中心',
    action         VARCHAR(64)  NOT NULL COMMENT '操作动作，如：新增用户',
    method         VARCHAR(255)          DEFAULT NULL COMMENT '目标方法：简单类名#方法名',
    request_method VARCHAR(10)           DEFAULT NULL COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    request_uri    VARCHAR(512)          DEFAULT NULL COMMENT '请求路径',
    request_params TEXT                  DEFAULT NULL COMMENT '脱敏后的入参 JSON，超长截断',
    result_status  TINYINT      NOT NULL DEFAULT 1 COMMENT '执行结果：1 成功，0 失败',
    error_msg      VARCHAR(1000)         DEFAULT NULL COMMENT '失败原因，仅记录业务异常文案或异常类型',
    ip             VARCHAR(64)           DEFAULT NULL COMMENT '客户端IP',
    cost_ms        BIGINT                DEFAULT NULL COMMENT '耗时（毫秒）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (log_id),
    KEY idx_user_time (user_id, create_time),
    KEY idx_time (create_time),
    KEY idx_module_action (module, action)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志表';
