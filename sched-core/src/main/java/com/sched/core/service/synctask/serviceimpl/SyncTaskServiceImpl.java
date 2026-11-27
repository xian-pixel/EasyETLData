package com.sched.core.service.synctask.serviceimpl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sched.core.config.SyncConfigLoader;
import com.sched.core.dto.synctask.SyncTaskCreateDTO;
import com.sched.core.dto.synctask.SyncTaskUpdateDTO;
import com.sched.core.service.synctask.SyncTaskService;
import com.sched.core.support.BizException;
import com.sched.core.vo.synctask.SyncTaskVO;
import com.sched.dao.entity.synctask.SyncTask;
import com.sched.dao.mapper.SyncTaskMapper;
import com.sched.enmu.EnableStatus;
import com.sched.enmu.ResultValue;
import com.sched.enmu.TableRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 同步任务服务实现。
 * <p>
 * 约定：
 * 1) 不用 @TableLogic，删除时把 del_flag 置 1，所有查询显式带 del_flag = 0；
 * 2) 源表／目标表必须已经在白名单里启用，否则任务建了也跑不起来，所以在保存前就校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncTaskServiceImpl extends ServiceImpl<SyncTaskMapper, SyncTask> implements SyncTaskService {

    /** 未删除 */
    private static final int NOT_DELETED = 0;
    /** 已删除 */
    private static final int DELETED = 1;
    /** 单页最大条数 */
    private static final int MAX_PAGE_SIZE = 200;

    private final SyncConfigLoader syncConfigLoader;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(SyncTaskCreateDTO dto) {
        checkTaskCodeUnique(dto.getTaskCode(), null);
        checkWhitelistEnabled(dto.getSourceTable(), dto.getTargetTable());

        SyncTask task = new SyncTask();
        BeanUtils.copyProperties(dto, task);
        // task_id 是字符串型雪花 ID，显式生成后写入（@TableId 使用 INPUT）
        task.setTaskId(String.valueOf(IdWorker.getId()));
        task.setDelFlag(NOT_DELETED);
        task.setEnableStatus(EnableStatus.ENABLED.getCode());

        save(task);
        log.info("新增同步任务成功 taskId={} taskCode={}", task.getTaskId(), task.getTaskCode());
        return task.getTaskId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String taskId, SyncTaskUpdateDTO dto) {
        SyncTask exists = requireActiveTask(taskId);
        checkWhitelistEnabled(dto.getSourceTable(), dto.getTargetTable());

        SyncTask update = new SyncTask();
        BeanUtils.copyProperties(dto, update);
        update.setTaskId(exists.getTaskId());       // 主键不可改
        // task_code / del_flag / enable_status 不在 DTO 里，不会被覆盖
        updateById(update);
        log.info("修改同步任务成功 taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String taskId) {
        requireActiveTask(taskId);
        boolean removed = lambdaUpdate()
                .eq(SyncTask::getTaskId, taskId)
                .eq(SyncTask::getDelFlag, NOT_DELETED)
                .set(SyncTask::getDelFlag, DELETED)
                .set(SyncTask::getEnableStatus, EnableStatus.DISABLED.getCode())
                .update();
        if (!removed) {
            throw new BizException(ResultValue.DATA_NOT_FOUND, "任务不存在或已删除");
        }
        log.info("逻辑删除同步任务成功 taskId={}", taskId);
    }

    @Override
    public SyncTaskVO detail(String taskId) {
        return toVO(requireActiveTask(taskId));
    }

    @Override
    public IPage<SyncTaskVO> page(int pageNum, int pageSize, String keyword) {
        int current = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        Page<SyncTask> page = new Page<>(current, size);
        var wrapper = Wrappers.<SyncTask>lambdaQuery()
                .eq(SyncTask::getDelFlag, NOT_DELETED);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SyncTask::getTaskCode, keyword)
                    .or().like(SyncTask::getSourceTable, keyword)
                    .or().like(SyncTask::getTargetTable, keyword));
        }
        wrapper.orderByDesc(SyncTask::getTaskId);
        page(page, wrapper);

        Page<SyncTaskVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String taskId, boolean enabled) {
        requireActiveTask(taskId);
        boolean updated = lambdaUpdate()
                .eq(SyncTask::getTaskId, taskId)
                .eq(SyncTask::getDelFlag, NOT_DELETED)
                .set(SyncTask::getEnableStatus,
                        enabled ? EnableStatus.ENABLED.getCode() : EnableStatus.DISABLED.getCode())
                .update();
        if (!updated) {
            throw new BizException(ResultValue.DATA_NOT_FOUND, "任务不存在或已删除");
        }
        log.info("同步任务状态切换 taskId={} enabled={}", taskId, enabled);
    }

    /** 查询未删除的任务，查不到统一抛"数据不存在" */
    private SyncTask requireActiveTask(String taskId) {
        SyncTask task = lambdaQuery()
                .eq(SyncTask::getTaskId, taskId)
                .eq(SyncTask::getDelFlag, NOT_DELETED)
                .one();
        if (task == null) {
            throw new BizException(ResultValue.DATA_NOT_FOUND, "同步任务不存在或已删除");
        }
        return task;
    }

    /** task_code 在未删除范围内必须唯一；excludeTaskId 用于修改时排除自己 */
    private void checkTaskCodeUnique(String taskCode, String excludeTaskId) {
        var wrapper = Wrappers.<SyncTask>lambdaQuery()
                .eq(SyncTask::getTaskCode, taskCode)
                .eq(SyncTask::getDelFlag, NOT_DELETED);
        if (StringUtils.hasText(excludeTaskId)) {
            wrapper.ne(SyncTask::getTaskId, excludeTaskId);
        }
        Long count = count(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ResultValue.DATA_DUPLICATED, "任务编码已存在：" + taskCode);
        }
    }

    /** 源表／目标表必须在白名单中启用 */
    private void checkWhitelistEnabled(String sourceTable, String targetTable) {
        if (syncConfigLoader.loadWhitelist(sourceTable, TableRole.SOURCE.getCode()) == null) {
            throw new BizException(ResultValue.TASK_CONFIG_INVALID, "源表未配置白名单：" + sourceTable);
        }
        if (syncConfigLoader.loadWhitelist(targetTable, TableRole.TARGET.getCode()) == null) {
            throw new BizException(ResultValue.TASK_CONFIG_INVALID, "目标表未配置白名单：" + targetTable);
        }
    }

    private SyncTaskVO toVO(SyncTask task) {
        SyncTaskVO vo = new SyncTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }
}
