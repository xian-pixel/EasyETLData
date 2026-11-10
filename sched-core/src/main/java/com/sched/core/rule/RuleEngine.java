package com.sched.core.rule;

import com.ql.util.express.ExpressRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
            runner.addFunctionOfServiceMethod("加",mathService,"add",new Class[]{String.class,String.class},null);
        }catch(Exception e){
            throw new RuntimeException("初始化规则引擎失败",e);
        }
    }
}
