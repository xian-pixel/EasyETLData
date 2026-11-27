package com.sched.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sched.dao.entity.file.FileRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件元数据 Mapper。
 */
@Mapper
public interface FileRecordMapper extends BaseMapper<FileRecord> {
}
