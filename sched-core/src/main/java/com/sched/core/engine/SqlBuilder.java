package com.sched.core.engine;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 动态 SQL 构建器。
 * 铁律：表名/列名来自配置，绝不能直接拼进 SQL——
 * 必须①格式校验 ②白名单校验，两步都过才允许拼。
 */
public class SqlBuilder {
    /**
     * 只允许字母、数字、下划线。挡住 "user;drop" 这类注入。
     */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private SqlBuilder() {
    }

    //查询sql生成器
    public static String buildSelect(String sourceTable, List<String> sourceColumns, List<String> orderByColumns, String whitelistColumns,String whereClause) {
        assertSafeName(sourceTable, "表");          // 表名只做格式校验
        Set<String> allowed = toAllowedSet(whitelistColumns, sourceTable);
        sourceColumns.forEach(c -> assertAllowed(c, allowed, "列"));
        orderByColumns.forEach(c -> assertAllowed(c, allowed, "排序列"));

        String cols = String.join(", ", sourceColumns);
        String orderBy = String.join(", ", orderByColumns);
        //增加where条件，为分片做准备
        String tail = (whereClause == null || whereClause.isBlank())? "" : " WHERE " + whereClause;
        return "SELECT " + cols + " FROM " + sourceTable + tail+ " ORDER BY " + orderBy;
    }

    //插入sql生成器
    public static String buildInsert(String targetTable,
                                     Collection<String> targetColumns,
                                     String whitelistColumns) {
        assertSafeName(targetTable, "表");
        Set<String> allowed = toAllowedSet(whitelistColumns, targetTable);
        targetColumns.forEach(c -> assertAllowed(c, allowed, "列"));

        String cols = String.join(", ", targetColumns);
        String marks = targetColumns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + targetTable + " (" + cols + ") VALUES (" + marks + ")";
    }

    /**
     * 白名单字符串 → 去空格、去空元素、去重的集合
     */
    private static Set<String> toAllowedSet(String whitelistColumns, String table) {
        if (whitelistColumns == null || whitelistColumns.isBlank()) {
            throw new IllegalStateException("表 " + table + " 未配置白名单列");
        }
        Set<String> allowed = Arrays.stream(whitelistColumns.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (allowed.isEmpty()) {
            throw new IllegalStateException("表 " + table + " 未配置白名单列");
        }
        return allowed;
    }

    private static void assertSafeName(String name, String what) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("非法的" + what + "名：" + name);
        }
    }

    private static void assertAllowed(String name, Set<String> allowed, String what) {
        assertSafeName(name, what);
        if (!allowed.contains(name)) {
            throw new IllegalArgumentException(what + "不在白名单：" + name + "，允许：" + allowed);
        }
    }
}
