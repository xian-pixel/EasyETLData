// sched-core/src/main/java/com/sched/core/registry/MasterLockService.java
// 纯数据操作，同样不带 @Scheduled —— 谁调用、多久调用一次是 admin 的事
package com.sched.core.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class MasterLockService {

    /** 本版只有一把锁；将来要加"任务级互斥锁"，复用这张表换 lock_name 即可 */
    private static final String LOCK_NAME = "sched:master";

    private final JdbcTemplate jdbcTemplate;
    /** 持有者标识，进程启动时生成一次，整个生命周期不变 —— 续约时靠它认出"这把锁还是我的" */
    private final String owner;
    /** 租约时长（秒）：主必须在这个时间内再次调用 tryAcquire 续约，否则视为放弃 */
    private final int leaseSeconds;

    public MasterLockService(JdbcTemplate jdbcTemplate,
                             @Value("${sched.executor.app-name:sched-admin}") String appName,
                             @Value("${sched.lock.lease-seconds:90}") int leaseSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        // 带上随机后缀：同一应用重启后 owner 不同，日志里能区分"上一轮的主"和"这一轮的主"
        this.owner = appName + "@" + UUID.randomUUID().toString().substring(0, 8);
        this.leaseSeconds = leaseSeconds;
        log.info("选主锁初始化 owner={} leaseSeconds={}", owner, leaseSeconds);
    }

    /**
     * 抢主 + 续约，一个方法承担两种语义：
     * 锁空闲/已过期 → 抢到，返回 true；
     * 锁是我自己的 → 顺带续约，返回 true；
     * 锁在别人手里且没到期 → 返回 false。
     * 因为主每轮扫描前都会调它，所以"续约"不需要单独的定时任务。
     */
    public boolean tryAcquire() {
        // 1首次部署时锁行不存在。INSERT IGNORE 而不是 INSERT：
        //    两台实例同时启动会并发插同一主键，用它避免抛 DuplicateKeyException 打断启动流程。
        //    expire_at 给一个过去时间，语义等于"这把锁从没被人拿过"。
        jdbcTemplate.update(
                "INSERT IGNORE INTO t_sync_lock(lock_name, owner, expire_at)"
                        + " VALUES(?, ?, DATE_SUB(NOW(), INTERVAL 1 SECOND))",
                LOCK_NAME, owner);

        // 2核心一条 SQL。WHERE 里同时包含"已过期"和"持有者是我"两个条件：
        //    - 已过期 → 原主死了/放弃续约，我可以接管；
        //    - 是我   → 我本来就在主，续约不需要先等自己过期。
        //    并发安全完全由 MySQL 的行锁保证，应用层不需要 synchronized，
        //    而且进程被 kill -9 也不会死锁 —— 到期后别人自动能抢到。
        int rows = jdbcTemplate.update(
                "UPDATE t_sync_lock SET owner=?, expire_at=DATE_ADD(NOW(), INTERVAL ? SECOND)"
                        + " WHERE lock_name=? AND (expire_at < NOW() OR owner=?)",
                owner, leaseSeconds, LOCK_NAME, owner);
        boolean master = rows >= 1;
        if (master) {
            log.debug("当前持有选主锁 owner={}", owner);
        }
        return master;
    }

    /** 可选：优雅停机时主动让锁，把故障转移的空窗从"一个租约"缩到 0 */
    public void release() {
        try {
            jdbcTemplate.update(
                    "UPDATE t_sync_lock SET expire_at=DATE_SUB(NOW(), INTERVAL 1 SECOND)"
                            + " WHERE lock_name=? AND owner=?",
                    LOCK_NAME, owner);
            log.info("已主动释放选主锁 owner={}", owner);
        } catch (Exception e) {
            // 停机阶段数据源可能已经关闭，这里失败不影响正确性：最坏是多等一个租约
            log.warn("释放选主锁失败 owner={}", owner, e);
        }
    }
}