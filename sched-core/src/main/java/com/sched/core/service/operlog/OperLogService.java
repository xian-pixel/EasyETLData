package com.sched.core.service.operlog;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sched.core.vo.operlog.OperLogVO;
import com.sched.dao.entity.operlog.OperLogRecord;

/**
 * 操作日志服务。
 * 写入由 OperLogAspect 统一调用，业务代码不直接依赖本接口。
 */
public interface OperLogService extends IService<OperLogRecord> {

    /**
     * 写入一条操作日志；内部补全 username 快照。
     * 调用方需自行保证本方法异常不外抛，避免影响主业务。
     */
    void record(OperLogRecord record);

    /**
     * 分页查询操作日志，keyword 匹配登录名/模块/动作。
     */
    IPage<OperLogVO> pageLogs(int pageNum, int pageSize, String keyword);
}
