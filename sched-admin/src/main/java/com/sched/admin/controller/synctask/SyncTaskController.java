package com.sched.admin.controller.synctask;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sched.annotation.OperLog;
import com.sched.core.dto.synctask.SyncTaskCreateDTO;
import com.sched.core.service.synctask.SyncTaskService;
import com.sched.core.dto.synctask.SyncTaskUpdateDTO;
import com.sched.core.vo.synctask.SyncTaskVO;
import com.sched.entity.dto.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 同步任务配置管理接口。
 * 写操作统一带 @OperLog，自动记入 t_oper_log。
 */
@RestController
@RequestMapping("/api/sync-task")
@RequiredArgsConstructor
public class SyncTaskController {

    private final SyncTaskService syncTaskService;

    /** 新增任务，返回生成的 taskId */
    @OperLog(module = "同步任务", action = "新增任务")
    @PostMapping
    public Result<String> create(@Valid @RequestBody SyncTaskCreateDTO dto) {
        return Result.success(syncTaskService.create(dto));
    }

    /** 修改任务（taskCode 不可改） */
    @OperLog(module = "同步任务", action = "修改任务")
    @PutMapping("/{taskId}")
    public Result<Void> update(@PathVariable String taskId,
                               @Valid @RequestBody SyncTaskUpdateDTO dto) {
        syncTaskService.update(taskId, dto);
        return Result.success();
    }

    /** 逻辑删除任务 */
    @OperLog(module = "同步任务", action = "删除任务", recordParams = false)
    @DeleteMapping("/{taskId}")
    public Result<Void> delete(@PathVariable String taskId) {
        syncTaskService.delete(taskId);
        return Result.success();
    }

    /** 任务详情 */
    @GetMapping("/{taskId}")
    public Result<SyncTaskVO> detail(@PathVariable String taskId) {
        return Result.success(syncTaskService.detail(taskId));
    }

    /** 分页查询 */
    @GetMapping("/page")
    public Result<IPage<SyncTaskVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String keyword) {
        return Result.success(syncTaskService.page(pageNum, pageSize, keyword));
    }

    /** 启用 / 停用 */
    @OperLog(module = "同步任务", action = "切换任务状态")
    @PutMapping("/{taskId}/status")
    public Result<Void> changeStatus(@PathVariable String taskId,
                                     @RequestParam boolean enabled) {
        syncTaskService.changeStatus(taskId, enabled);
        return Result.success();
    }
}
