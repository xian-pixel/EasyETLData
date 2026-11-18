package com.sched.admin.engine;

import com.sched.core.config.SyncConfigLoader;
import com.sched.core.engine.ConfigDrivenRunner;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 并发工程服务
 */
@Service
@Slf4j
public class ShardRunner {

    private final ConfigDrivenRunner runner;
    private final SyncConfigLoader syncConfigLoader;
    // 线程池，每个任务最多跑 4 个分片
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    public ShardRunner(ConfigDrivenRunner runner, SyncConfigLoader syncConfigLoader) {
        this.runner = runner;
        this.syncConfigLoader = syncConfigLoader;
    }

    /** 分片总数只认库里的配置，不接受外部手传，避免两处不一致导致漏片 */
    public List<Long> runAll(String taskCode) throws Exception {
        int shardTotal = syncConfigLoader.loadTask(taskCode).shardTotal();
        if (shardTotal <= 0) {
            shardTotal = 1;             // 兜底：配置漏填时按全量跑，不要跑 0 片
        }
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < shardTotal; i++) {
            final int shardIndex = i;
            final int finalShardTotal = shardTotal;
            futures.add(pool.submit(() -> runner.runShard(taskCode, shardIndex, finalShardTotal)));
        }
        List<Long> counts = new ArrayList<>();
        for (Future<Long> f : futures) {
            counts.add(f.get());        // get() 会把子线程的异常抛回来，不要吞
        }
        return counts;
    }
    /**
     * 故障转移专用：后台重跑单一片。
     * 这里用 pool.submit 而不是直接 runner.runShard：
     * 一个分片可能要跑几分钟，同步调用会把定时任务线程占住，心跳也跟着停。
     */
    public void submitOne(String taskCode, int shardIndex, int shardTotal) {
        pool.submit(() -> {
            try {
                runner.runShard(taskCode, shardIndex, shardTotal);
            } catch (Exception e) {
                // 线程池里抛出的异常没人接得住，必须自己记；这一片重跑失败需人工介入
                log.error("故障转移重跑失败 taskCode={} shard={}/{}",
                        taskCode, shardIndex, shardTotal, e);
            }
        });
    }

    /** 容器关闭时释放线程池，否则正在跑的分片会被硬砍 */
    @PreDestroy
    public void shutdown() {
        pool.shutdown();
    }
}