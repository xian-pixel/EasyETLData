package com.sched.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sched.dao.entity.synctask.SyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 同步任务 Mapper。
 * <p>
 * 继承 BaseMapper 后即拥有 selectById / insert / updateById / selectPage 等通用方法；
 * 下面几个方法是 BaseMapper 覆盖不了的复杂 SQL，实现在
 * resources/com/sched/dao/mapper/SyncTaskMapper.xml 里。
 */
@Mapper
public interface SyncTaskMapper extends BaseMapper<SyncTask> {

    /** 动态条件查询：任意条件组合，只过滤未删除 */
    List<SyncTask> selectByCondition(@Param("taskCode") String taskCode,
                                     @Param("sourceTable") String sourceTable,
                                     @Param("targetTable") String targetTable,
                                     @Param("enableStatus") String enableStatus);

    /** 按 ID 集合批量查询 */
    List<SyncTask> selectByIds(@Param("ids") List<String> ids);

    /** 批量插入，一次 SQL 写完 */
    int batchInsert(@Param("list") List<SyncTask> list);

    /** 选择性更新：只更新非 null 字段，不碰 task_code / del_flag */
    int updateSelective(@Param("task") SyncTask task);

    /** 未删除范围内同 task_code 的数量；excludeId 用于修改时排除自己 */
    int countActiveByCode(@Param("taskCode") String taskCode,
                          @Param("excludeId") String excludeId);
}
