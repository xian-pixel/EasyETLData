-- ============================================================
-- 调度平台建库脚本（MySQL 8.0+）
--
-- 执行顺序（全部在 sched_platform 库内执行）：
--   1. sql/00_init_database.sql                         建库并切库
--   2. sql/同步主线/01_sync_task.sql                    任务定义 / 字段映射 / 表列白名单
--   3. sql/同步主线/02_sync_log.sql                     批次日志 / 失败明细
--   4. sql/同步主线/03_sync_rule.sql                    规则头 / 条件 / 动作
--   5. sql/同步主线/04_sync_cluster.sql                 执行器注册 / 选主租约锁
--   6. sql/演示数据/demo_order_detail.sql               演示源表与目标表
--   7. sql/个人中心/personal_center.sql                 平台用户 / 文件元数据 + 联调用户
--   8. sql/操作日志/oper_log.sql                        操作日志（AOP 审计）
--
-- 说明：
--   - 同步主线与个人中心/操作日志互不依赖，可单独执行；
--   - 演示表只在本地跑 /api/dev/** 联调时需要，生产环境不要创建；
--   - 其余脚本均带 IF NOT EXISTS，重复执行不会报错（但也不会变更已存在表结构）。
-- ============================================================

CREATE DATABASE IF NOT EXISTS sched_platform
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE sched_platform;
