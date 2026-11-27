package com.sched.core.config;

import com.sched.core.engine.model.FieldMapping;
import com.sched.core.engine.model.SyncTaskConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 从库里读任务配置。
 * 说明：配置只有少数几行，用 JdbcTemplate 直查更直观；配置管理的增删改界面后期才用 MyBatis-Plus。
 */
@Component
public class SyncConfigLoader {

    private static final String TASK_SQL =
            "SELECT task_id, task_code, source_table, target_table, sync_type,"
                    + " batch_size, biz_key, shard_total, block_strategy"
                    + " FROM t_sync_task WHERE task_code = ? AND del_flag = 0 AND enable_status = '1'";

    private static final String MAPPING_SQL =
            "SELECT source_column, target_column, value_type, value"
                    + " FROM t_sync_field_mapping WHERE task_id = ? AND enable_status = '1'"
                    + " ORDER BY field_seq";

    private final JdbcTemplate jdbcTemplate;

    public SyncConfigLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SyncTaskConfig loadTask(String taskCode) {
        return jdbcTemplate.queryForObject(TASK_SQL, (rs, i) -> new SyncTaskConfig( //在rs里按照列名取值付给SyncTaskConfig对象
                rs.getString("task_id"),
                rs.getString("task_code"),
                rs.getString("source_table"),
                rs.getString("target_table"),
                rs.getString("sync_type"),
                rs.getInt("batch_size"),
                rs.getString("biz_key"),
                rs.getInt("shard_total"),
                rs.getString("block_strategy")), taskCode);
    }

    public List<FieldMapping> loadMappings(String taskId) {
        return jdbcTemplate.query(MAPPING_SQL, (rs, i) -> new FieldMapping(
                rs.getString("source_column"),
                rs.getString("target_column"),
                rs.getString("value_type"),
                rs.getString("value")), taskId);
    }

    /**
     * 查白名单里的列，返回逗号分隔字符串；查不到返回 null
     */
    public String loadWhitelist(String tableName, String role) {
        List<String> list = jdbcTemplate.query(
                "SELECT column_names FROM t_sync_table_whitelist"
                        + " WHERE table_name = ? AND table_role = ? AND enable_status = '1'",
                (rs, i) -> rs.getString("column_names"), tableName, role);
        return list.isEmpty() ? null : list.get(0);
    }
}