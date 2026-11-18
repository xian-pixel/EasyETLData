// sched-admin/src/main/java/com/sched/admin/job/FailoverJob.java
package com.sched.admin.job;

import com.sched.admin.engine.ShardRunner;
import com.sched.core.model.StuckBatch;
import com.sched.core.registry.ExecutorRegistryService;
import com.sched.core.registry.MasterLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class FailoverJob {

    private final ExecutorRegistryService registryService;
    private final MasterLockService masterLockService;
    private final ShardRunner shardRunner;
    private final int batchTimeoutMinutes;

    public FailoverJob(ExecutorRegistryService registryService,
                       MasterLockService masterLockService,
                       ShardRunner shardRunner,
                       @Value("${sched.failover.batch-timeout-minutes:30}") int batchTimeoutMinutes) {
        this.registryService = registryService;
        this.masterLockService = masterLockService;
        this.shardRunner = shardRunner;
        this.batchTimeoutMinutes = batchTimeoutMinutes;
    }

    @Scheduled(initialDelay = 10_000, fixedDelayString = "${sched.failover.interval-ms:30000}")
    public void scanAndFailover() {
        try {
            // 1  抢主：抢不到说明别的实例在主事，这台不重复扫描
            if (!masterLockService.tryAcquire()) {
                log.debug("非主节点，本轮跳过故障转移");
                return;
            }
            // 2  摘除失联执行器（先摘，避免把任务派给已经死掉的执行器）
            int disabled = registryService.disableDead();
            if (disabled > 0) {
                log.warn("已摘除 {} 个失联执行器", disabled);
            }
            // 3  找僵尸批次，逐个转移
            List<StuckBatch> stuck = registryService.findStuckBatches(batchTimeoutMinutes);
            for (StuckBatch b : stuck) {
                // 幂等闸门：只有把 RUNNING 改成 FAILED 成功的那台才有资格重跑
                if (!registryService.markFailed(b.batchId(), b.shardIndex(), "执行器失联，已转移")) {
                    continue;                       // 别的实例已经处理过，直接跳过
                }
                log.warn("僵尸批次转移 batchId={} taskCode={} shard={}/{}",
                        b.batchId(), b.taskCode(), b.shardIndex(), b.shardTotal());
                // 重跑必须沿用原来的 shardIndex / shardTotal，绝不重新分配（铁律）
                shardRunner.submitOne(b.taskCode(), b.shardIndex(), b.shardTotal());
            }
        } catch (Exception e) {
            log.error("故障转移扫描失败，本轮跳过", e);
        }
    }
}