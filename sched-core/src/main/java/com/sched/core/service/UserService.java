package com.sched.core.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sched.core.dto.ChangePasswordDTO;
import com.sched.core.dto.UserCreateDTO;
import com.sched.core.dto.UserUpdateDTO;
import com.sched.core.dto.vo.UserVO;
import com.sched.dao.entity.SchedUser;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心用户服务。
 */
public interface UserService extends IService<SchedUser> {

    /** 新增用户（登录名唯一，密码 BCrypt 加密后落库） */
    UserVO createUser(UserCreateDTO dto);

    /** 修改指定用户资料，仅允许本人操作 */
    UserVO updateUser(Long userId, UserUpdateDTO dto);

    /** 逻辑删除指定用户，仅允许本人操作 */
    void deleteUser(Long userId);

    /** 查询用户详情（不含密码） */
    UserVO getUserDetail(Long userId);

    /** 分页查询用户，keyword 匹配登录名/昵称/真实姓名 */
    IPage<UserVO> pageUsers(int pageNum, int pageSize, String keyword);

    /** 当前登录用户资料 */
    UserVO getCurrentUser();

    /** 修改当前登录用户资料 */
    UserVO updateCurrentUser(UserUpdateDTO dto);

    /** 修改当前登录用户密码 */
    void changePassword(ChangePasswordDTO dto);

    /** 上传当前用户头像并回填 avatar_file_id */
    UserVO uploadAvatar(MultipartFile file);
}
