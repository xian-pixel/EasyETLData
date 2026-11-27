package com.sched.core.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sched.core.dto.vo.OperLogVO;
import com.sched.core.service.OperLogService;
import com.sched.dao.entity.OperLogRecord;
import com.sched.dao.entity.SchedUser;
import com.sched.dao.mapper.OperLogMapper;
import com.sched.dao.mapper.SchedUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 操作日志实现。
 * 写入路径要足够"轻"：一次按主键查用户名 + 一次 insert，失败由调用方（切面）兜住。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogServiceImpl extends ServiceImpl<OperLogMapper, OperLogRecord> implements OperLogService {

    private static final int MAX_PAGE_SIZE = 200;

    private final SchedUserMapper userMapper;

    @Override
    public void record(OperLogRecord record) {
        fillUsername(record);
        save(record);
    }

    @Override
    public IPage<OperLogVO> pageLogs(int pageNum, int pageSize, String keyword) {
        Page<OperLogRecord> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE));
        LambdaQueryWrapper<OperLogRecord> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OperLogRecord::getUsername, keyword)
                    .or().like(OperLogRecord::getModule, keyword)
                    .or().like(OperLogRecord::getAction, keyword));
        }
        wrapper.orderByDesc(OperLogRecord::getCreateTime);
        page(page, wrapper);

        Page<OperLogVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<OperLogVO> records = page.getRecords().stream().map(this::toVO).toList();
        result.setRecords(records);
        return result;
    }

    /**
     * 补全操作人登录名快照；用户已不存在时保持为空，不影响日志写入。
     */
    private void fillUsername(OperLogRecord record) {
        if (record.getUserId() == null || StringUtils.hasText(record.getUsername())) {
            return;
        }
        SchedUser user = userMapper.selectById(record.getUserId());
        if (user != null) {
            record.setUsername(user.getUsername());
        }
    }

    private OperLogVO toVO(OperLogRecord record) {
        OperLogVO vo = new OperLogVO();
        vo.setLogId(record.getLogId());
        vo.setUserId(record.getUserId());
        vo.setUsername(record.getUsername());
        vo.setModule(record.getModule());
        vo.setAction(record.getAction());
        vo.setMethod(record.getMethod());
        vo.setRequestMethod(record.getRequestMethod());
        vo.setRequestUri(record.getRequestUri());
        vo.setRequestParams(record.getRequestParams());
        vo.setResultStatus(record.getResultStatus());
        vo.setErrorMsg(record.getErrorMsg());
        vo.setIp(record.getIp());
        vo.setCostMs(record.getCostMs());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }
}
