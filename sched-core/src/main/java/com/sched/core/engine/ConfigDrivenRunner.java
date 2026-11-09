package com.sched.core.engine;

import com.sched.core.config.SyncConfigLoader;
import com.sched.core.log.BatchLogService;
import com.sched.core.model.FieldMapping;
import com.sched.core.model.SyncTaskConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    //注入日志服务
    private final BatchLogService batchLogService;
    //注入事务
    private final TransactionTemplate transactionTemplate;

    public ConfigDrivenRunner(JdbcTemplate jdbcTemplate,SyncConfigLoader syncConfigLoader,BatchLogService batchLogService,TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.syncConfigLoader = syncConfigLoader;
        this.batchLogService = batchLogService;
        this.transactionTemplate = transactionTemplate;
    }
    public long run (String taskCode){
        //读取配置
        SyncTaskConfig taskConfig = syncConfigLoader.loadTask(taskCode); //通过SyncConfigLoader读取配置并且赋值给taskConfig对象
        long start = System.currentTimeMillis();
        //开始执行日志
        String batchId = batchLogService.start(taskConfig.taskId(),0,taskConfig.shardTotal());
        long total = 0;
        //尝试执行核心搬运逻辑
        try{
            //添加事务
            total = transactionTemplate.execute(status -> runOnce(taskCode, taskConfig));
            //执行结束日志
            batchLogService.finish(batchId,0,"SUCCESS",total,0,null,System.currentTimeMillis()-start);
        }catch (Exception e){
            //执行失败日志
            log.error("任务 {} 执行失败", taskCode, e);
            batchLogService.finish(batchId,0,"FAIL",total,total,e.getMessage(),System.currentTimeMillis()-start);
            //将异常抛出
            throw e;
        }


        return total;
    }

    //同步核心搬运逻辑
//    @Transactional(rollbackFor = Exception.class)//添加事务，如果有异常，全部回滚
    public long runOnce(String taskCode,SyncTaskConfig taskConfig){
        List<FieldMapping> fieldMappings = syncConfigLoader.loadMappings(taskConfig.taskId());//通过SyncConfigLoader读取配置并且赋值给fieldMappings对象
        String sourceWhitelist = syncConfigLoader.loadWhitelist(taskConfig.sourceTable(), "SOURCE");//查询该任务的源表是否在白名单配置表里
        String targetWhitelist = syncConfigLoader.loadWhitelist(taskConfig.targetTable(), "TARGET");//查询该任务的目标表是否在白名单配置表里
        //进行判断
        if (sourceWhitelist == null || targetWhitelist == null) {
            throw new IllegalStateException("表未配置白名单：" + taskConfig.sourceTable() + " / " + taskConfig.targetTable());
        }
        //按照映射算出需要读取的字段映射(目标列必须取全部字段的值，因为目标列需要填充datasource)
        List<String> sourceColumnList = fieldMappings.stream().filter(m -> "COLUMN".equals(m.valueType())).map(FieldMapping::sourceColumn).toList();
        List<String> targetColumnList = fieldMappings.stream().map(FieldMapping::targetColumn).toList();
        //读取需要排序的字段list
        List<String> orderByList = List.of(taskConfig.bizKey().split(","));
        //构建查询sql
        String selectSql = SqlBuilder.buildSelect(taskConfig.sourceTable(), sourceColumnList, orderByList, sourceWhitelist);
        //构建插入sql
        String insertSql = SqlBuilder.buildInsert(taskConfig.targetTable(), targetColumnList, targetWhitelist);
        log.info("sql构建完成，查询sql为：{}，\n插入sql为：{}",selectSql,insertSql);
        // 先删后插，删除task任务源表的数据(目前是全量删除)
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
                    //获取fieldmapping里字段值类型是否是column，以便填充datasource字段值
                    FieldMapping m = fieldMappings.get(i);
                    arg[i] = "COLUMN".equals(m.valueType())
                            ? row.get(m.sourceColumn())
                            : m.value();
                }
                args.add(arg);
                log.info("要插入的值是{}",args.toArray());
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
