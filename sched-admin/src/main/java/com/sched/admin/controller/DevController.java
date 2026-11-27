package com.sched.admin.controller;

import com.sched.core.engine.ShardRunner;
import com.sched.core.engine.model.SyncReturnVo;
import com.sched.enmu.ResultValue;
import com.sched.entity.dto.vo.Result;
import com.sched.core.engine.ConfigDrivenRunner;
import com.sched.core.engine.MiniSyncRunner;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dev")
@Slf4j
public class DevController {
    @Resource
    private MiniSyncRunner miniSyncRunner;
    @Resource
    private ConfigDrivenRunner configDrivenRunner;
    @Resource
    private ShardRunner shardRunner;

    @GetMapping("/run-once")
    public Map<String, Object> runOnce() {
        //定义一个map用于返回结果result
        Map<String, Object> result = new LinkedHashMap<>();
        log.info("开始执行一次全量同步~~~");
        long rows = miniSyncRunner.runOnce();//rows记录总行数
        log.info("全量同步执行完成~~~");
        result.put("rows", rows);
        return result;
    }

    @GetMapping("/run-once-config")
    public Result<Map<String, Object>> runOnceConfig(@RequestParam String taskCode) {
        //定义一个map用于返回结果result
        Map<String, Object> map = new LinkedHashMap<>();
        log.info("开始执行一次全量同步~~~");
        long rows = configDrivenRunner.run(taskCode);
        map.put("result:", new SyncReturnVo<Long>(taskCode, rows));
        log.info("全量同步执行完成~~~");
        return Result.<Map<String, Object>>builder()
                .code(ResultValue.SUCCESS.getCode())
                .msg(ResultValue.SUCCESS.getMsg())
                .data(map)
                .build();
    }



    @GetMapping("/run-shard")
    public Result<Map<String, Object>> runShard(@RequestParam String taskCode) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        log.info("开始执行分片同步~~~");
        List<Long> counts = shardRunner.runAll(taskCode);            // 每片行数，顺序即分片号
        long total = counts.stream().mapToLong(Long::longValue).sum();
        map.put("shards", counts);
        map.put("total", total);
        log.info("分片同步执行完成~~~ 各片={} 合计={}", counts, total);
        return Result.<Map<String, Object>>builder()
                .code(ResultValue.SUCCESS.getCode())
                .msg(ResultValue.SUCCESS.getMsg())
                .data(map)
                .build();
    }
}
