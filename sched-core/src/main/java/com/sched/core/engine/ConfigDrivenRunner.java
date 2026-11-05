package com.sched.core.engine;

import com.sched.core.config.SyncConfigLoader;
import com.sched.core.model.FieldMapping;
import com.sched.core.model.SyncTaskConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ConfigDrivenRunner {
    //注入JdbcTemplate
    private final JdbcTemplate jdbcTemplate;
    //注入SyncConfigLoader，从库里读配置
    private final SyncConfigLoader syncConfigLoader;

    public ConfigDrivenRunner(JdbcTemplate jdbcTemplate,SyncConfigLoader syncConfigLoader) {
        System.out.println("准备注入使用动态sql构造器~~~~");
        this.jdbcTemplate = jdbcTemplate;
        this.syncConfigLoader = syncConfigLoader;
        System.out.println("注入完成~~~");
    }
    public long runOnce(String taskCode){
        //读取配置
        SyncTaskConfig taskConfig = syncConfigLoader.loadTask(taskCode); //通过SyncConfigLoader读取配置并且赋值给taskConfig对象
        List<FieldMapping> fieldMappings = syncConfigLoader.loadMappings(taskConfig.taskId());//通过SyncConfigLoader读取配置并且赋值给fieldMappings对象
        String sourceWhitelist = syncConfigLoader.loadWhitelist(taskConfig.sourceTable(), "SOURCE");//查询该任务的源表是否在白名单配置表里
        String targetWhitelist = syncConfigLoader.loadWhitelist(taskConfig.targetTable(), "TARGET");//查询该任务的目标表是否在白名单配置表里
        //进行判断
        if (sourceWhitelist == null || targetWhitelist == null) {
            throw new IllegalStateException("表未配置白名单：" + taskConfig.sourceTable() + " / " + taskConfig.targetTable());
        }
        //按照映射算出需要读取的字段映射
        List<String> sourceColumnList = fieldMappings.stream().filter(m -> "COLUMN".equals(m.valueType())).map(FieldMapping::sourceColumn).toList();
        List<String> targetColumnList = fieldMappings.stream().filter(m -> "COLUMN".equals(m.valueType())).map(FieldMapping::targetColumn).toList();
        //读取需要排序的字段list
        List<String> orderByList = List.of(taskConfig.bizKey().split(","));
        //构建查询sql
        String selectSql = SqlBuilder.buildSelect(taskConfig.sourceTable(), sourceColumnList, orderByList, sourceWhitelist);
        //构建插入sql
        String insertSql = SqlBuilder.buildInsert(taskConfig.targetTable(), targetColumnList, targetWhitelist);
        log.info("sql构建完成，查询sql为：{}，插入sql为：{}",selectSql,insertSql);
        // 先删后插，删除task任务源表的数据(目前是全量删除)//TODO：后续可根据sync_type来判断是增量还是全量
        jdbcTemplate.update("DELETE FROM " + taskConfig.targetTable());
        log.info("已清空目标表 {}", taskConfig.targetTable());
        //分批执行查询sql
        long offset = 0;
        long total = 0;
        while(true){
            List<Map<String, Object>> resultList = jdbcTemplate.queryForList(selectSql + " LIMIT ? OFFSET ?", taskConfig.batchSize(), offset);
            //对查出来的数据进行判断
            if (resultList.isEmpty()) {
                break;
            }
            //如果resultList不是空，那么拼接插入数据
            List<Object[]> args = new ArrayList<>(resultList.size());
            for (Map<String, Object> row : resultList) {
                Object[] arg = new Object[targetColumnList.size()];
                for (int i = 0; i < targetColumnList.size(); i++) {
                    arg[i] = row.get(sourceColumnList.get(i));
                }
                args.add(arg);
            }
            //执行插入sql
            jdbcTemplate.batchUpdate(insertSql, args);
            total += resultList.size();
            offset += resultList.size();
            log.info("任务 {} 进度：{} 行", taskCode, total);
        }
        return total;
    }

}
