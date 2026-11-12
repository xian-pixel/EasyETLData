package com.sched.utils;

public class CommonUtils {
    /**
     * 下划线命名转驼峰：net_amount → netAmount。
     * 为什么必须转：t_sync_rule_condition.property_name 配的是驼峰，
     * 而数据库查出来的列名是下划线；不转就永远匹配不上，表现是“规则配了但不生效”。
     */
    public static final String camel(String column) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;                      // 标记“下一个字符要转大写”

        for (char ch : column.toCharArray()) {      // 逐字符遍历
            if (ch == '_') {
                upper = true;                       // 遇到下划线：本身不输出，只把开关打开
            } else {
                // upper 为 true 就把当前字符转成大写，然后复位开关
                sb.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return sb.toString();
    }
}
