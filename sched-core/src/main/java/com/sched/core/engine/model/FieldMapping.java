package com.sched.core.engine.model;

/** 字段映射（从 t_sync_field_mapping 读出来的一行） */
public record FieldMapping(
        String sourceColumn,
        String targetColumn,
        String valueType,   // COLUMN / TEXT
        String value) {
}
