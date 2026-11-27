package com.sched.core.dto.synctask;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增同步任务的入参。
 * 表名／列名只允许字母数字下划线，从源头挡掉注入（这些值后续会被拼进 SQL）。
 */
@Data
public class SyncTaskCreateDTO {

    @NotBlank(message = "任务编码不能为空")
    @Pattern(regexp = "[A-Za-z0-9_]{2,64}", message = "任务编码只能包含字母、数字、下划线，长度 2-64")
    private String taskCode;

    @NotBlank(message = "源表不能为空")
    @Pattern(regexp = "[A-Za-z0-9_]+", message = "源表名只能包含字母、数字、下划线")
    private String sourceTable;

    @NotBlank(message = "目标表不能为空")
    @Pattern(regexp = "[A-Za-z0-9_]+", message = "目标表名只能包含字母、数字、下划线")
    private String targetTable;

    @NotBlank(message = "同步类型不能为空")
    @Size(max = 32, message = "同步类型最长 32 个字符")
    private String syncType;

    @NotNull(message = "批次大小不能为空")
    @Min(value = 1, message = "批次大小至少为 1")
    @Max(value = 100000, message = "批次大小不能超过 100000")
    private Integer batchSize;

    @NotBlank(message = "业务主键不能为空")
    @Size(max = 256, message = "业务主键最长 256 个字符")
    private String bizKey;

    @NotNull(message = "分片总数不能为空")
    @Min(value = 1, message = "分片总数至少为 1")
    @Max(value = 64, message = "分片总数不能超过 64")
    private Integer shardTotal;

    @Size(max = 64, message = "分片策略最长 64 个字符")
    private String blockStrategy;
}
