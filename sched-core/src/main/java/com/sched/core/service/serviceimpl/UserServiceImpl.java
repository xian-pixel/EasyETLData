package com.sched.core.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sched.core.dto.ChangePasswordDTO;
import com.sched.core.dto.UserCreateDTO;
import com.sched.core.dto.UserUpdateDTO;
import com.sched.core.dto.vo.FileVO;
import com.sched.core.dto.vo.UserVO;
import com.sched.core.service.FileService;
import com.sched.core.service.UserService;
import com.sched.core.support.BizException;
import com.sched.core.support.CurrentUserProvider;
import com.sched.dao.entity.SchedUser;
import com.sched.dao.mapper.SchedUserMapper;
import com.sched.enmu.ResultValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 个人中心用户服务实现。
 * 约定：所有查询显式带 deleted = 0（未使用 @TableLogic），删除写入删除时间戳。
 * 权限：当前无角色体系，写操作一律校验"操作人 == 目标用户"；
 * 接入 Spring Security 后可在 Controller 加 @PreAuthorize 放开管理员能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SchedUserMapper, SchedUser> implements UserService {

    /** 未删除标记：0 */
    private static final long NOT_DELETED = 0L;

    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final FileService fileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(UserCreateDTO dto) {
        Long exists = lambdaQuery()
                .eq(SchedUser::getUsername, dto.getUsername())
                .eq(SchedUser::getDeleted, NOT_DELETED)
                .count();
        if (exists != null && exists > 0) {
            throw new BizException(ResultValue.DATA_DUPLICATED, "登录名已存在");
        }
        SchedUser user = new SchedUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setRealName(dto.getRealName());
        user.setMobile(dto.getMobile());
        user.setEmail(dto.getEmail());
        user.setGender(dto.getGender() == null ? 0 : dto.getGender());
        user.setProfile(dto.getProfile());
        user.setDeptName(dto.getDeptName());
        user.setStatus(1);
        user.setDeleted(NOT_DELETED);
        user.setVersion(0);
        save(user);
        log.info("新增用户成功 userId={} username={}", user.getUserId(), user.getUsername());
        return toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(Long userId, UserUpdateDTO dto) {
        checkSelf(userId);
        requireActiveUser(userId);
        SchedUser update = new SchedUser();
        update.setUserId(userId);
        copyProfileFields(dto, update);
        updateById(update);
        return toVO(requireActiveUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        checkSelf(userId);
        requireActiveUser(userId);
        boolean removed = lambdaUpdate()
                .eq(SchedUser::getUserId, userId)
                .eq(SchedUser::getDeleted, NOT_DELETED)
                .set(SchedUser::getDeleted, System.currentTimeMillis())
                .set(SchedUser::getStatus, 0)
                .update();
        if (!removed) {
            throw new BizException(ResultValue.DATA_NOT_FOUND, "用户不存在或已删除");
        }
        log.info("逻辑删除用户 userId={}", userId);
    }

    @Override
    public UserVO getUserDetail(Long userId) {
        return toVO(requireActiveUser(userId));
    }

    @Override
    public IPage<UserVO> pageUsers(int pageNum, int pageSize, String keyword) {
        Page<SchedUser> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        LambdaQueryWrapper<SchedUser> wrapper = Wrappers.<SchedUser>lambdaQuery()
                .eq(SchedUser::getDeleted, NOT_DELETED);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SchedUser::getUsername, keyword)
                    .or().like(SchedUser::getNickname, keyword)
                    .or().like(SchedUser::getRealName, keyword));
        }
        wrapper.orderByDesc(SchedUser::getCreateTime);
        page(page, wrapper);

        Page<UserVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<UserVO> records = page.getRecords().stream().map(this::toVO).toList();
        result.setRecords(records);
        return result;
    }

    @Override
    public UserVO getCurrentUser() {
        return toVO(requireActiveUser(currentUserProvider.currentUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateCurrentUser(UserUpdateDTO dto) {
        Long userId = currentUserProvider.currentUserId();
        SchedUser update = new SchedUser();
        update.setUserId(userId);
        copyProfileFields(dto, update);
        if (!updateById(update)) {
            throw new BizException(ResultValue.DATA_NOT_FOUND, "用户不存在或已删除");
        }
        return getCurrentUser();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto) {
        Long userId = currentUserProvider.currentUserId();
        SchedUser user = requireActiveUser(userId);
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultValue.PARAM_ERROR, "原密码不正确");
        }
        SchedUser update = new SchedUser();
        update.setUserId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        updateById(update);
        log.info("用户修改密码成功 userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO uploadAvatar(MultipartFile file) {
        Long userId = currentUserProvider.currentUserId();
        requireActiveUser(userId);
        FileVO fileVO = fileService.upload(file, "AVATAR", userId);
        SchedUser update = new SchedUser();
        update.setUserId(userId);
        update.setAvatarFileId(fileVO.getFileId());
        updateById(update);
        return getCurrentUser();
    }

    /**
     * 查询未删除用户，查不到统一抛"数据不存在"
     */
    private SchedUser requireActiveUser(Long userId) {
        SchedUser user = lambdaQuery()
                .eq(SchedUser::getUserId, userId)
                .eq(SchedUser::getDeleted, NOT_DELETED)
                .one();
        if (user == null) {
            throw new BizException(ResultValue.DATA_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 无角色体系下的兜底校验：只允许操作自己
     */
    private void checkSelf(Long userId) {
        if (userId == null || !userId.equals(currentUserProvider.currentUserId())) {
            throw new BizException(ResultValue.NO_PERMISSION, "只能操作自己的账号");
        }
    }

    /**
     * 仅复制允许修改的资料字段，避免请求体越权修改 username / password / status
     */
    private void copyProfileFields(UserUpdateDTO dto, SchedUser target) {
        target.setNickname(dto.getNickname());
        target.setRealName(dto.getRealName());
        target.setMobile(dto.getMobile());
        target.setEmail(dto.getEmail());
        target.setGender(dto.getGender());
        target.setProfile(dto.getProfile());
        target.setDeptName(dto.getDeptName());
    }

    private UserVO toVO(SchedUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    private long normalizePageNum(int pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    private long normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
