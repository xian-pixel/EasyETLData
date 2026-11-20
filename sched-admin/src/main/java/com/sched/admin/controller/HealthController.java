package com.sched.admin.controller;

import com.sched.entity.dto.vo.Result;
import com.sched.enmu.ResultValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 探活：只要服务还活着就返回 200，库挂了也只把 db 标成 down，不抛异常。
     * 因此这里直接查库、不走业务 service —— 探活本身不承担业务语义。
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            data.put("db", one != null && one == 1 ? "up" : "down");
        } catch (Exception e) {
            // 探活失败必须就地截住并留日志，否则库不可用时健康检查接口自己先 500
            log.warn("健康检查数据库连接失败", e);
            data.put("db", "down");
        }
        return Result.<Map<String, Object>>builder()
                .code(ResultValue.SUCCESS.getCode())
                .msg(ResultValue.SUCCESS.getMsg())
                .data(data)
                .build();
    }
}
