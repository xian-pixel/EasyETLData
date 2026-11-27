package com.sched.core.engine.log;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class ErrorDetailService {
    private JdbcTemplate jdbcTemplate;

    public ErrorDetailService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)

    public void record(String batchId, int shardIndex, String errorType, String errorMsg) {
        jdbcTemplate.update(
                "INSERT INTO t_sync_error_detail(error_id, batch_id, shard_index, error_type, error_msg)"
                        + " VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString().replace("-", ""), batchId, shardIndex,
                errorType, errorMsg == null ? null : errorMsg.substring(0, Math.min(errorMsg.length(), 1000)));
    }
}
