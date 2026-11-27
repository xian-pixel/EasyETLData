// ① sched-core/src/main/java/com/sched/core/registry/ExecutorRegistryService.java
// 只做数据操作，不带 @Scheduled —— 这样 HTTP 查询、别的入口都能复用它
package com.sched.core.registry;

import com.sched.core.model.StuckBatch;
import com.sched.enmu.BatchStatus;
import com.sched.enmu.EnableStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ExecutorRegistryService {

    private final JdbcTemplate jdbcTemplate;
    private final String appName;
    private final String address;
    private final int heartbeatTimeoutSeconds;
    // 进程启动时生成一次；重启即换新 ID，能区分"上一轮的我"
    private final String executorId = UUID.randomUUID().toString().replace("-", "");

    public ExecutorRegistryService(JdbcTemplate jdbcTemplate,
                                   @Value("${sched.executor.app-name:sched-admin}") String appName,
                                   @Value("${sched.executor.address:}") String configuredAddress,
                                   @Value("${server.port:8080}") int port,
                                   @Value("${sched.heartbeat.timeout-seconds:90}") int heartbeatTimeoutSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.appName = appName;
        this.address = resolveAddress(configuredAddress, port);
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        log.info("执行器注册初始化 executorId={} appName={} address={}", executorId, appName, this.address);
    }

    /**
     * 地址优先取配置；没配就自动探测本机 IP + server.port，绝不写死 127.0.0.1:8090
     */
    private static String resolveAddress(String configured, int port) {
        if (configured != null && !configured.isBlank()) {
            return configured;                       // 多网卡 / 容器环境显式指定，优先
        }
        try {
            return InetAddress.getLocalHost().getHostAddress() + ":" + port;
        } catch (UnknownHostException e) {
            log.warn("自动探测本机 IP 失败，退化为 127.0.0.1，请显式配置 sched.executor.address", e);
            return "127.0.0.1:" + port;
        }
    }

    /**
     * 心跳：一条 SQL 完成"注册或续约"，依赖 uk_app_addr 唯一键
     */
    public void beat() {
        jdbcTemplate.update(
                "INSERT INTO t_sync_executor(executor_id, app_name, address, last_heartbeat, enable_status)"
                        + " VALUES(?,?,?,NOW(),?)"
                        + " ON DUPLICATE KEY UPDATE executor_id=VALUES(executor_id),"
                        + " last_heartbeat=NOW(), enable_status=?",
                executorId, appName, address,
                EnableStatus.ENABLED.getCode(), EnableStatus.ENABLED.getCode());
        log.debug("心跳上报 executorId={} address={}", executorId, address);
    }

    /**
     * 摘除：心跳超过阈值置为停用，返回摘除行数
     */
    public int disableDead() {
        return jdbcTemplate.update(
                "UPDATE t_sync_executor SET enable_status=?"
                        + " WHERE last_heartbeat < DATE_SUB(NOW(), INTERVAL ? SECOND)"
                        + " AND enable_status=?",
                EnableStatus.DISABLED.getCode(), heartbeatTimeoutSeconds, EnableStatus.ENABLED.getCode());
    }

    /**
     * 僵尸批次：状态还是 RUNNING，但开始时间早于阈值。task_code 靠 JOIN 拿，因为 batch_log 里只存 task_id
     */
    public List<StuckBatch> findStuckBatches(int timeoutMinutes) {
        return jdbcTemplate.query(
                "SELECT b.batch_id, t.task_code, b.shard_index, b.shard_total"
                        + " FROM t_sync_batch_log b JOIN t_sync_task t ON t.task_id = b.task_id"
                        + " WHERE b.status=?"
                        + " AND b.start_time < DATE_SUB(NOW(), INTERVAL ? MINUTE)",
                (rs, i) -> new StuckBatch(
                        rs.getString("batch_id"),
                        rs.getString("task_code"),
                        rs.getInt("shard_index"),
                        rs.getInt("shard_total")),
                BatchStatus.RUNNING.getCode(), timeoutMinutes);
    }

    /**
     * 标记失败：带 status='RUNNING' 守卫，只有真正把状态改过来的那台返回 true
     */
    public boolean markFailed(String batchId, int shardIndex, String reason) {
        int rows = jdbcTemplate.update(
                "UPDATE t_sync_batch_log SET status=?, end_time=NOW(), error_msg=?"
                        + " WHERE batch_id=? AND shard_index=? AND status=?",
                BatchStatus.FAILED.getCode(), reason, batchId, shardIndex, BatchStatus.RUNNING.getCode());
        return rows > 0;
    }
}