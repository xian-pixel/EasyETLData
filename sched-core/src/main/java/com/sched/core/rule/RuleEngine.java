package com.sched.core.rule;

import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.sched.utils.CommonUtils;

@Component
@Slf4j
public class RuleEngine {
    //引擎实例，创建一次反复用
    private final ExpressRunner runner = new ExpressRunner();
    private final JdbcTemplate jdbcTemplate;    // 用来读三张规则配置表
    private final RuleMathService mathService;  // 金额加减的真正实现者

    //构造器注入
    public RuleEngine (JdbcTemplate jdbcTemplate,RuleMathService mathService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mathService = mathService;
        //初始化规则引擎
        try{
            runner.addFunctionOfServiceMethod("减",mathService,"minus",new Class[]{String.class,String.class},null);
            runner.addFunctionOfServiceMethod("加",mathService,"plus",new Class[]{String.class,String.class},null);
        }catch(Exception e){
            throw new RuntimeException("初始化规则引擎失败",e);
        }
    }


    /**
     *对一行数据应用该任务下所有规则，返回转换后的 Map（key 统一为驼峰）。
     *注意返回的是新 Map，不直接改入参 row，避免调用方拿到改了一半的数据。
     */
    public Map<String,Object> apply(String taskId, Map<String,Object> row) throws Exception{
        //定义一个结果集
        Map<String, Object> result = new HashMap<>();
        //对row进行遍历，赋值给result
        row.forEach((key,value)-> result.put(key,value));
        // QLExpress 的变量池。表达式里出现的 amount、taxAmount 这些名字，都从这个容器里取
        DefaultContext<String, Object> ctx = new DefaultContext<>();
        //方法引用，将result的key和value分别赋值给ctx的key和value
        result.forEach(ctx::put);
        // 查出该任务下所有启用的规则，priority 小的先执行（数的规则先算，依赖它的规则后算）
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                "SELECT rule_id FROM t_sync_rule WHERE task_id = ? AND enable_status = '1' ORDER BY priority",
                taskId);
        //进行规则的遍历
        for(Map<String,Object> r : rules){
            // queryForList 返回 List<Map>，取出来是 Object，要强转成 String
            String ruleId = (String) r.get("rule_id");
            // 把这条规则的所有条件拼成表达式串
            String condition = buildCondition(ruleId);
            // 执行条件表达式，5 个参数依次是：
            //   1) condition   表达式字符串
            //   2) ctx         变量上下文（表达式里的 amount 从这里查）
            //   3) null        错误回调，传 null = 出错直接抛异常（千万不能吞，见 7.6）
            //   4) true        isCache，把编译结果缓存住，同一条规则下次不再编译（性能关键）
            //   5) false       isTrace，是否打印执行过程，生产必须 false
            Object hit = runner.execute(condition, ctx, null, true, false);
            // 用 Boolean.TRUE.equals(hit) 而不是 hit == true：
            // execute 返回的是 Object，万一返回 null，写 (Boolean) hit 会直接 NPE
            if (Boolean.TRUE.equals(hit)) {
                // 条件成立，执行这条规则下的赋值动作
                applyAssignments(ruleId, ctx, result);
            }
        }
        return result;
    }

    /**
     * 把 t_sync_rule_condition 里的多行条件拼成一个表达式。
     * 拼接规则：同一个 area_num 内部用 && 连接（组内 AND），不同 area_num 之间用 || 连接（组间 OR）。
     */
    private String buildCondition(String ruleId) {

        // 按组号 area_num、组内顺序 sort_no 取出所有条件
        List<Map<String, Object>> conds = jdbcTemplate.queryForList(
                "SELECT area_num, property_name, operator, value, value_type"
                        + " FROM t_sync_rule_condition WHERE rule_id = ? ORDER BY area_num, sort_no", ruleId);

        // 一条条件都没配：返回 true，表示这条规则无条件执行
        if (conds.isEmpty()) {
            return "true";
        }
        // TreeMap 会按 key 自动升序排列，保证 area_num=1 的组一定排在 area_num=2 前面
        // 不用 HashMap 的原因：它不保证顺序，拼出来的表达式每次顺序都不一样，排查时对不上
        Map<Integer, StringBuilder> byArea = new java.util.TreeMap<>();

        for (Map<String, Object> c : conds) {
            String right = "COLUMN".equals(c.get("value_type"))
                    ? String.valueOf(c.get("value"))                       // 列名：taxAmount
                    : "'" + c.get("value") + "'";                          // 字面量：'VIP'
            // 拼表达式
            String expr = c.get("property_name") + " " + c.get("operator") + " " + right;
            // 将area_num转换成Number类型
            int area = ((Number) c.get("area_num")).intValue();
            // computeIfAbsent：这个组号还没有 StringBuilder 就新建一个放进去，有就直接取出来
            // 然后 append 把 “条件 && ” 接到这个组后面
            // 等价老写法：
            //   if (!byArea.containsKey(area)) byArea.put(area, new StringBuilder());
            //   byArea.get(area).append(expr).append(" && ");
            byArea.computeIfAbsent(area, k -> new StringBuilder())
                    .append(expr)
                    .append(" && ");
        }

        StringBuilder all = new StringBuilder();
        //中间是或者的关系
        byArea.values().forEach(sb ->
                all.append("(")
                        .append(sb.substring(0, sb.length() - 4))
                        .append(") || "));

        // 去掉最后一个 " || "，得到最终表达式
        return all.substring(0, all.length() - 4);
    }

    /**
     * 执行一条规则下的所有赋值动作，把结果写回 result 和 ctx。
     */
    private void applyAssignments(String ruleId, DefaultContext<String, Object> ctx,
                                  Map<String, Object> result) throws Exception {

        // 按顺序取出这条规则的所有赋值动作
        List<Map<String, Object>> assigns = jdbcTemplate.queryForList(
                "SELECT target_column, value_type, value FROM t_sync_rule_assignment"
                        + " WHERE rule_id = ? AND enable_status = '1' ORDER BY sort_no", ruleId);

        for (Map<String, Object> a : assigns) {

            // value_type = EXPR  → value 是一段表达式，要执行才能拿到结果
            // value_type 是别的  → value 就是常量
            Object value = "EXPR".equals(a.get("value_type"))
                    ? runner.execute((String) a.get("value"), ctx, null, true, false)
                    : a.get("value");

            // 目标列名同样转驼峰，和 result 的 key 保持一致
            String key = CommonUtils.camel((String) a.get("target_column"));

            // 写回最终结果，后面组装 INSERT 参数时从这里取值
            result.put(key, value);

            // 同时写回 ctx：同一行上后面的规则可以引用前面刚算出来的值
            // 例如规则 2 的条件写 netAmount > '100'，能取到值靠的就是这一行
            ctx.put(key, value);
        }
    }


}
