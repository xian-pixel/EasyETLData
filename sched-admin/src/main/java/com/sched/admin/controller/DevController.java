package com.sched.admin.controller;

import com.sched.core.model.SyncReturnVo;
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
import java.util.Map;

@RestController
@RequestMapping("/api/dev")
@Slf4j
public class DevController {
    @Resource
    private MiniSyncRunner miniSyncRunner;
    @Resource
    private ConfigDrivenRunner configDrivenRunner;
    @GetMapping("/run-once")
    public Map<String, Object> runOnce(){
        //定义一个map用于返回结果result
        Map<String, Object> result = new LinkedHashMap<>();
        log.info("开始执行一次全量同步~~~");
        long rows = miniSyncRunner.runOnce();//rows记录总行数
        log.info("全量同步执行完成~~~");
        result.put("rows", rows);
        return result;
    }

    @GetMapping("/run-once-config")
    public Result<Map<String, Object>> runOnceConfig(@RequestParam String taskCode){
        //定义一个map用于返回结果result
        Map<String, Object> map = new LinkedHashMap<>();
        log.info("开始执行一次全量同步~~~");
        long rows = configDrivenRunner.run( taskCode);
        map.put("result:", new SyncReturnVo<Long>(taskCode,rows));
        log.info("全量同步执行完成~~~");
        return Result.<Map<String, Object>>builder()
                     .code(ResultValue.SUCCESS.getCode())
                     .msg(ResultValue.SUCCESS.getMsg())
                     .data(map)
                     .build();
    }
}
