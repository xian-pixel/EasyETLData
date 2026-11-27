-- ============================================================
-- 个人中心模块建表脚本（MySQL 8.0+）
--
-- 设计约定：
-- 1) 业务表不存二进制内容，文件只存元数据 + objectKey；
-- 2) 逻辑删除统一用 deleted 列：0 未删除，非 0 为删除时间戳（毫秒），
--    因此唯一键可以带上 deleted，支持"同名用户删除后重新注册"；
-- 3) 主键由 MyBatis-Plus ASSIGN_ID（雪花算法）生成，类型 BIGINT；
-- 4) 表名/列名与实体注解完全一致，不要自行改名。
-- ============================================================

CREATE DATABASE IF NOT EXISTS sched_platform
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE sched_platform;

-- ------------------------------------------------------------
-- 平台用户表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sched_user
(
    user_id         BIGINT       NOT NULL COMMENT '用户ID（雪花）',
    username        VARCHAR(64)  NOT NULL COMMENT '登录名',
    password        VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希，禁止明文/MD5',
    nickname        VARCHAR(64)           DEFAULT NULL COMMENT '昵称',
    real_name       VARCHAR(64)           DEFAULT NULL COMMENT '真实姓名',
    avatar_file_id  BIGINT                DEFAULT NULL COMMENT '头像文件ID，关联 t_sched_file.file_id',
    mobile          VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    email           VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
    gender          TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0 未知，1 男，2 女',
    profile         VARCHAR(500)          DEFAULT NULL COMMENT '个人简介',
    dept_name       VARCHAR(128)          DEFAULT NULL COMMENT '所属部门（展示冗余）',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    last_login_time DATETIME              DEFAULT NULL COMMENT '最近登录时间',
    create_by       BIGINT                DEFAULT NULL COMMENT '创建人ID',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT                DEFAULT NULL COMMENT '更新人ID',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，非 0 为删除时间戳',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_username_deleted (username, deleted),
    KEY idx_mobile (mobile),
    KEY idx_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='平台用户表';

-- ------------------------------------------------------------
-- 文件元数据表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sched_file
(
    file_id       BIGINT       NOT NULL COMMENT '文件ID（雪花）',
    biz_type      VARCHAR(32)  NOT NULL DEFAULT 'ATTACHMENT' COMMENT '业务类型：AVATAR/ATTACHMENT/EXPORT',
    biz_id        BIGINT                DEFAULT NULL COMMENT '关联业务ID，如头像对应 userId',
    uploader_id   BIGINT       NOT NULL COMMENT '上传人ID，下载与查询必须按此过滤',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名，仅用于展示与下载命名',
    ext           VARCHAR(32)           DEFAULT NULL COMMENT '小写扩展名，不含点',
    content_type  VARCHAR(128)          DEFAULT NULL COMMENT 'MIME 类型',
    file_size     BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    storage_type  VARCHAR(16)  NOT NULL DEFAULT 'LOCAL' COMMENT '存储类型：LOCAL/MINIO/OSS',
    object_key    VARCHAR(512) NOT NULL COMMENT '服务端生成的存储相对路径/对象Key',
    md5           CHAR(32)              DEFAULT NULL COMMENT '文件 MD5，用于秒传/去重',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 有效，0 失效',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted       BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，非 0 为删除时间戳',
    PRIMARY KEY (file_id),
    KEY idx_uploader (uploader_id, deleted),
    KEY idx_biz (biz_type, biz_id),
    KEY idx_md5 (md5)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='文件元数据表';

-- ------------------------------------------------------------
-- 本地联调演示用户：user_id = 1，对应 application.yml 的 sched.security.dev-user-id
--
-- 说明：password 是占位哈希，当前工程尚未接入登录接口，不会校验密码；
--      接入登录后请通过 POST /api/user 创建正式用户，并把 dev-user-id 改成新用户ID，
--      同时删除本段演示数据。切勿把占位哈希用于生产。
-- ------------------------------------------------------------
INSERT INTO t_sched_user (user_id, username, password, nickname, real_name, gender, status, deleted, version)
SELECT 1, 'dev', '$2a$10$PLACEHOLDER_PLEASE_RESET_BY_API_000000000000000000000000',
       '联调用户', '联调用户', 0, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM (SELECT user_id FROM t_sched_user WHERE user_id = 1) t);
