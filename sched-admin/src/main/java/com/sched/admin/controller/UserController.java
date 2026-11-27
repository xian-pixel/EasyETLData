package com.sched.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sched.annotation.OperLog;
import com.sched.core.dto.ChangePasswordDTO;
import com.sched.core.dto.UserCreateDTO;
import com.sched.core.dto.UserUpdateDTO;
import com.sched.core.dto.vo.UserVO;
import com.sched.core.service.UserService;
import com.sched.entity.dto.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心用户接口。
 * 当前用户身份由 CurrentUserProvider 提供，请求体中不接受 userId，避免越权。
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 新增用户 */
    @OperLog(module = "个人中心", action = "新增用户")
    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.success(userService.createUser(dto));
    }

    /** 修改指定用户资料（服务层校验只能改自己） */
    @OperLog(module = "个人中心", action = "修改用户")
    @PutMapping("/{userId}")
    public Result<UserVO> update(@PathVariable Long userId, @Valid @RequestBody UserUpdateDTO dto) {
        return Result.success(userService.updateUser(userId, dto));
    }

    /** 逻辑删除指定用户（服务层校验只能删自己） */
    @OperLog(module = "个人中心", action = "删除用户", recordParams = false)
    @DeleteMapping("/{userId}")
    public Result<Void> delete(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return Result.success();
    }

    /** 用户详情 */
    @GetMapping("/{userId}")
    public Result<UserVO> detail(@PathVariable Long userId) {
        return Result.success(userService.getUserDetail(userId));
    }

    /** 分页查询 */
    @GetMapping("/page")
    public Result<IPage<UserVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize,
                                      @RequestParam(required = false) String keyword) {
        return Result.success(userService.pageUsers(pageNum, pageSize, keyword));
    }

    /** 当前登录用户资料 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(userService.getCurrentUser());
    }

    /** 修改当前登录用户资料 */
    @OperLog(module = "个人中心", action = "修改个人资料")
    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UserUpdateDTO dto) {
        return Result.success(userService.updateCurrentUser(dto));
    }

    /** 修改当前登录用户密码 */
    @OperLog(module = "个人中心", action = "修改密码")
    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return Result.success();
    }

    /** 上传头像，返回最新用户信息（含 avatarFileId） */
    @OperLog(module = "个人中心", action = "上传头像")
    @PostMapping("/me/avatar")
    public Result<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(userService.uploadAvatar(file));
    }
}
