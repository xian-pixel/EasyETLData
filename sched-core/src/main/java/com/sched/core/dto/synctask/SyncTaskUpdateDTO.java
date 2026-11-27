package com.sched.core.dto.synctask;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改同步任务的入参。
 * task_code 作为业务标识不允许修改，所以这里不包含该字段。
 */
@Data
public class SyncTaskUpdateDTO {

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
