//sched-admin/src/main/java/com/sched/admin/job/ExecutorHeartbeatJob.java
// 只负责"多久跑一次"，一行业务逻辑都没有 —— 这是 admin 该干的事
package com.sched.admin.job;

import com.sched.core.engine.registry.ExecutorRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 心跳保活
 */
@Component
@Slf4j
public class ExecutorHeartbeatJob {

    private final ExecutorRegistryService registryService;
    // 注入registryService服务，用于心跳摘除和更新等操作
    public ExecutorHeartbeatJob(ExecutorRegistryService registryService) {
        this.registryService = registryService;
    }
    //首次执行延迟事件5秒，fixedDelayString:上一次执行完成之后，等待30秒
    @Scheduled(initialDelay = 5_000, fixedDelayString = "${sched.heartbeat.interval-ms:30000}")
    public void beat() {
        try {
            registryService.beat();
        } catch (Exception e) {
            // 定时任务里异常必须就地截住并留日志：这一轮失败没关系，别让异常逃逸影响下一轮
            log.error("心跳上报失败，本轮跳过", e);
        }
    }
}