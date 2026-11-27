package com.sched.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sched.core.dto.vo.OperLogVO;
import com.sched.core.service.OperLogService;
import com.sched.entity.dto.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询接口。
 * 查询本身不记日志，避免自我放大。
 */
@RestController
@RequestMapping("/api/oper-log")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    /** 分页查询操作日志，keyword 匹配登录名/模块/动作 */
    @GetMapping("/page")
    public Result<IPage<OperLogVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(operLogService.pageLogs(pageNum, pageSize, keyword));
    }
}
