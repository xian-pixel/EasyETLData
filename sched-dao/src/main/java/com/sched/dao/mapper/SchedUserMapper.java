package com.sched.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sched.dao.entity.user.SchedUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台用户 Mapper，包路径已加入 AdminApplication 的 @MapperScan。
 */
@Mapper
public interface SchedUserMapper extends BaseMapper<SchedUser> {
}
