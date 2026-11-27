package com.sched.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sched.dao.entity.SchedUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台用户 Mapper，包路径与 AdminApplication 的 @MapperScan("com.sched.dao.mapper") 一致。
 */
@Mapper
public interface SchedUserMapper extends BaseMapper<SchedUser> {
}
