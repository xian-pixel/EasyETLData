package com.sched.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sched.dao.entity.operlog.OperLogRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 * 继承 BaseMapper 后即拥有 insert / selectById / selectPage 等通用方法，
 * 无需 XML（日志写入只需要单表 insert）。
 */
@Mapper
public interface OperLogMapper extends BaseMapper<OperLogRecord> {
}
