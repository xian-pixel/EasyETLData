package com.sched.core.engine;


import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * M4 版最小同步器：把 src_order_detail 抄到 ods_order_detail。
 * 注意：这里是"故意写死"的教学版，SQL 与列名都固定。
 * 第 5 章会把它改造成"从库里读配置 + 白名单校验"。
 */
@Component
@Slf4j
public class MiniSyncRunner {
    //每批读取行数。先固定 1000。
    private static final int BATCH_SIZE = 1000;
    private final JdbcTemplate jdbcTemplate;
    //构造器注入
    public MiniSyncRunner(JdbcTemplate jdbcTemplate) {
        //准备注入构造器
        System.out.println("准备注入构造器~~");
        this.jdbcTemplate = jdbcTemplate;
        System.out.println("jdbcTemplate注入成功");
    }
    /**
     * 跑一次全量同步，返回成功的行数
     * @return
     */
    public long runOnce(){
        //记录开始事件
        long start = System.currentTimeMillis();
        //清空目标表
        jdbcTemplate.update("DELETE FROM ods_order_detail");
        log.info("已清空目标表 ods_order_detail");

        long offset = 0;
        long total = 0;

        // ② 分批循环：每批 1000 行，写完再读下一批。绝不一次读全表（会 OOM）
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT order_id, line_no, customer, amount, tax_amount"
                            + " FROM src_order_detail"
                            + " ORDER BY order_id, line_no"
                            + " LIMIT ? OFFSET ?",
                    BATCH_SIZE, offset);

            if (rows.isEmpty()) {
                break;
            }

            List<Object[]> args = new ArrayList<>(rows.size());
            Date now = new Date();
            for (Map<String, Object> row : rows) {
                args.add(new Object[]{
                        row.get("order_id"),
                        row.get("line_no"),
                        row.get("customer"),
                        row.get("amount"),
                        row.get("tax_amount"),
                        null,          // net_amount 先留空，第 7 章由规则算出来
                        "SRC",         // data_source 标记来源
                        now
                });
            }

            // ③ 批量写入：一次提交 1000 行，比逐行 INSERT 快一到两个数量级
            jdbcTemplate.batchUpdate(
                    "INSERT INTO ods_order_detail"
                            + "(order_id, line_no, customer, amount, tax_amount,"
                            + " net_amount, data_source, create_time)"
                            + " VALUES (?,?,?,?,?,?,?,?)",
                    args);

            total += rows.size();
            offset += BATCH_SIZE;
            log.info("同步进度：已写入 {} 行", total);
        }

        long cost = System.currentTimeMillis() - start;
        log.info("同步结束：共 {} 行，耗时 {} ms", total, cost);
        return total;
    }
}
