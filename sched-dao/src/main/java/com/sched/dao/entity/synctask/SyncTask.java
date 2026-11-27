package com.sched.dao.entity.synctask;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 同步任务配置，对应 t_sync_task。
 * <p>
 * 字段与 SyncConfigLoader.TASK_SQL 中的列一一对应。
 * 注意：del_flag 是逻辑删除标记，项目约定不用 @TableLogic（删除时写 1，查询时显式过滤 0）。
 */
@Data
@TableName("t_sync_task")
public class SyncTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 ID：雪花 ID 的字符串形式，由服务层生成后写入（INPUT 表示由应用赋值） */
    @TableId(value = "task_id", type = IdType.INPUT)
    private String taskId;

    /** 任务编码，全局唯一（未删除范围内） */
    private String taskCode;

    /** 源表名 */
    private String sourceTable;

    /** 目标表名 */
    private String targetTable;

    /** 同步类型：FULL / INCREMENTAL 等 */
    private String syncType;

    /** 每批读取行数 */
    private Integer batchSize;

    /** 业务主键列，多个用逗号分隔，如 order_id,line_no */
    private String bizKey;

    /** 分片总数，1 表示不分片 */
    private Integer shardTotal;

    /** 分片策略（预留） */
    private String blockStrategy;

    /** 逻辑删除标记：0 未删除，1 已删除 */
    private Integer delFlag;

    /** 启用状态：'1' 启用，'0' 停用 */
    private String enableStatus;
}
