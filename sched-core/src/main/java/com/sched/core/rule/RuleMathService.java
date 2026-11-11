package com.sched.core.rule;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 金额运算服务。
 * 存在的唯一理由：数据库里金额字段是 VARCHAR，在表达式里直接写 amount - taxAmount 会抛 ClassCastException。
 * 所以把它包装成两个方法，注册给 QLExpress 当函数用。
 */
@Service
public class RuleMathService {

    /**
     * 减法。注册后表达式里写：减(amount, taxAmount)
     *
     * @param a 被减数，字符串形式，例如 "100.00"
     * @param b 减数，字符串形式
     * @return BigDecimal，避免 double 的精度误差
     */
    public BigDecimal minus(String a, String b) {
        // 任一为空就返回 0：宁可得 0 也不让一行脏数据把整批任务搞失败
        // 代价是会掩盖“源数据缺失”，所以上游的非空校验要单独做
        if (a == null || b == null) {
            return BigDecimal.ZERO;
        }
        // trim() 去掉数据库里可能带的空格：' 100.00 ' 直接 new BigDecimal 会抛 NumberFormatException
        // subtract 是 BigDecimal 的精确减法，不会出现 0.1 + 0.2 = 0.30000000000000004 这种问题
        return new BigDecimal(a.trim()).subtract(new BigDecimal(b.trim()));
    }

    /** 加法。注册后表达式里写：加(a, b)，逻辑与 minus 完全一样，只是换成 add */
    public BigDecimal plus(String a, String b) {
        if (a == null || b == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(a.trim()).add(new BigDecimal(b.trim()));
    }
}