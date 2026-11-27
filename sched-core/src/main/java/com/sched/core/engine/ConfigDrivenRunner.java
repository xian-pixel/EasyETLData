package com.sched.core.engine;

import com.sched.core.config.SyncConfigLoader;
import com.sched.core.engine.log.BatchLogService;
import com.sched.core.engine.log.ErrorDetailService;
import com.sched.core.engine.model.FieldMapping;
import com.sched.core.engine.model.SyncTaskConfig;
import com.sched.core.engine.rule.RuleEngine;
import com.sched.enmu.BatchStatus;
import com.sched.enmu.ErrorType;
import com.sched.enmu.TableRole;
import com.sched.enmu.ValueType;
import com.sched.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
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
    //注入错误详情服务
    private final ErrorDetailService errorDetailService;
    //注入规则引擎
    private final RuleEngine ruleEngine;

    public ConfigDrivenRunner(JdbcTemplate jdbcTemplate, SyncConfigLoader syncConfigLoader, BatchLogService batchLogService, TransactionTemplate transactionTemplate
            , ErrorDetailService errorDetailService, RuleEngine ruleEngine) {
        this.jdbcTemplate = jdbcTemplate;
        this.syncConfigLoader = syncConfigLoader;
        this.batchLogService = batchLogService;
        this.transactionTemplate = transactionTemplate;
        this.errorDetailService = errorDetailService;
        this.ruleEngine = ruleEngine;
    }

    public long run(String taskCode) {
        //读取配置
        SyncTaskConfig taskConfig = syncConfigLoader.loadTask(taskCode); //通过SyncConfigLoader读取配置并且赋值给taskConfig对象
        long start = System.currentTimeMillis();
        //开始执行日志
        // 当前是单机执行，分片总数固定记 1；M8 接入分片后再传真实值
        String batchId = batchLogService.start(taskConfig.taskId(), 0, 1);
        long total = 0;
        //尝试执行核心搬运逻辑
        try {
            //添加事务
            total = transactionTemplate.execute(status -> runOnce(taskCode, taskConfig, batchId,null, 0, 1));
            //执行结束日志
            batchLogService.finish(batchId, 0, BatchStatus.SUCCESS.getCode(), total, 0, null, System.currentTimeMillis() - start);
        } catch (Exception e) {
            //执行失败日志
            log.error("任务 {} 执行失败", taskCode, e);
            batchLogService.finish(batchId, 0, BatchStatus.FAILED.getCode(), 0, total, e.getMessage(), System.currentTimeMillis() - start);
            //将异常抛出
            throw e;
        }


        return total;
    }
    public long runShard(String taskCode, int shardIndex, int shardTotal) {
        SyncTaskConfig taskConfig = syncConfigLoader.loadTask(taskCode);
        long start = System.currentTimeMillis();

        // ③ 日志必须写真实分片号，否则 4 片全记成 shard_index=0，8.5 铁律 3 的重试就失效
        String batchId = batchLogService.start(taskConfig.taskId(), shardIndex, shardTotal);

        String where = shardTotal <= 1 ? null
                : "MOD(CRC32(order_id), " + shardTotal + ") = " + shardIndex;

        long total = 0;
        try {
            total = transactionTemplate.execute(status ->
                    runOnce(taskCode, taskConfig, batchId, where, shardIndex, shardTotal));
            batchLogService.finish(batchId, shardIndex, BatchStatus.SUCCESS.getCode(), total, 0, null,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("分片执行失败 taskCode={} shard={}/{}", taskCode, shardIndex, shardTotal, e);
            batchLogService.finish(batchId, shardIndex, BatchStatus.FAILED.getCode(), 0, total, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;   // 必须抛：ShardRunner 里靠 f.get() 才能看到子线程的异常
        }
        return total;
    }

    //同步核心搬运逻辑（事务由 run() 里的 TransactionTemplate 控制，这里不能再加 @Transactional）
    public long runOnce(String taskCode, SyncTaskConfig taskConfig, String batchId,
                        String where, int shardIndex, int shardTotal) {
        List<FieldMapping> fieldMappings = syncConfigLoader.loadMappings(taskConfig.taskId());//通过SyncConfigLoader读取配置并且赋值给fieldMappings对象
        String sourceWhitelist = syncConfigLoader.loadWhitelist(taskConfig.sourceTable(), TableRole.SOURCE.getCode());//查询该任务的源表是否在白名单配置表里
        String targetWhitelist = syncConfigLoader.loadWhitelist(taskConfig.targetTable(), TableRole.TARGET.getCode());//查询该任务的目标表是否在白名单配置表里
        //进行判断
        if (sourceWhitelist == null || targetWhitelist == null) {
            throw new IllegalStateException("表未配置白名单：" + taskConfig.sourceTable() + " / " + taskConfig.targetTable());
        }
        //按照映射算出需要读取的字段映射(目标列必须取全部字段的值，因为目标列需要填充datasource)
        List<String> sourceColumnList = fieldMappings.stream().filter(m -> ValueType.COLUMN.is(m.valueType())).map(FieldMapping::sourceColumn).toList();
        List<String> targetColumnList = fieldMappings.stream().map(FieldMapping::targetColumn).toList();
        //读取需要排序的字段list
        List<String> orderByList = List.of(taskConfig.bizKey().split(","));
        //构建查询sql
        String selectSql = SqlBuilder.buildSelect(taskConfig.sourceTable(), sourceColumnList, orderByList, sourceWhitelist,where);
        //构建插入sql
        String insertSql = SqlBuilder.buildInsert(taskConfig.targetTable(), targetColumnList, targetWhitelist);
        log.info("sql构建完成，查询sql为：{}，\n插入sql为：{}", selectSql, insertSql);
        //先进行判断，判断源表查询的条数是否为0，如果为0，那么不进行删除目标表的数据
        if (jdbcTemplate.queryForList(selectSql + " LIMIT 1").isEmpty()) {
            if (where != null) {
                log.info("分片 {}/{} 本片无数据，跳过", shardIndex, shardTotal);
                return 0;                      // 空分片：不删目标表、不报异常
            }
            //抛出异常
            throw new IllegalStateException("源表查询的条数为0，不进行删除目标表的数据");
        }
        // 先删后插，删除task任务目标表的数据(修改为按照where条件删除)，也就是根据分片进行删除
        if (where == null) {
            jdbcTemplate.update("DELETE FROM " + taskConfig.targetTable());
        } else {
            jdbcTemplate.update("DELETE FROM " + taskConfig.targetTable() + " WHERE " + where);
        }
        log.info("已清空目标表 {}", taskConfig.targetTable());
        //分批执行查询sql
        long offset = 0;
        long total = 0;
        while (true) {
            List<Map<String, Object>> resultList = jdbcTemplate.queryForList(selectSql + " LIMIT ? OFFSET ?", taskConfig.batchSize(), offset);
            //对查出来的数据进行判断
            if (resultList.isEmpty()) {
                break;
            }
            //如果resultList不是空，那么拼接插入数据
            List<Object[]> args = new ArrayList<>(resultList.size());
            for (Map<String, Object> row : resultList) {
                // ① 新增：先跑规则，netAmount / net_amount 之类的计算值在这一步产生
                Map<String, Object> converted;
                try {
                    converted = ruleEngine.apply(taskConfig.taskId(), row);
                } catch (Exception e) {
                    // 规则异常必须留痕且让批次可见地失败，禁止静默跳过
                    log.error("任务 {} 规则执行失败", taskCode, e);
                    errorDetailService.record(batchId, shardIndex, ErrorType.RULE_EXECUTE_ERROR.getCode(), e.getMessage());
                    // 必须包装成运行时异常：apply 声明的是受检异常 Exception
                    throw new IllegalStateException("规则执行失败：" + e.getMessage(), e);
                }
                // ② 新增：再跑映射，把规则产生的值填充到目标表的字段里
                Object[] arg = new Object[targetColumnList.size()];
                for (int i = 0; i < targetColumnList.size(); i++) {
                    FieldMapping m = fieldMappings.get(i);
                    if (ValueType.RULE.is(m.valueType())) {
                        // 规则计算列：源表没有这一列，值只存在于 converted 里，且 key 已被转成驼峰
                        Object v = converted.get(m.targetColumn());
                        arg[i] = (v != null) ? v : converted.get(CommonUtils.camel(m.targetColumn()));
                    } else if (ValueType.COLUMN.is(m.valueType())) {
                        // 源表列：从规则转换后的行取值
                        Object v = converted.get(m.sourceColumn());
                        arg[i] = (v != null) ? v : converted.get(CommonUtils.camel(m.sourceColumn()));
                    } else {
                        // TEXT 等常量列：直接取配置的常量值
                        arg[i] = m.value();
                    }
                }
                args.add(arg);
            }
            //查找出报错记录是哪条
            try {
                //执行插入sql
                jdbcTemplate.batchUpdate(insertSql, args);
            } catch (Exception e) {
                errorDetailService.record(batchId, shardIndex, ErrorType.SQL_ERROR.getCode(), e.getMessage());
                throw e;      // 或者记录后继续下一批（取决于是否要求"部分成功"）
            }

            total += resultList.size();
            offset += resultList.size();
            log.info("任务 {} 进度：{} 行", taskCode, total);
        }
        return total;
    }

}
