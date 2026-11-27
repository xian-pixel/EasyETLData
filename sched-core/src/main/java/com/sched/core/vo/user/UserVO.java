package com.sched.core.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户对外视图：只暴露可展示字段，绝不包含 password。
 */
@Data
public class UserVO {

    private Long userId;

    private String username;

    private String nickname;

    private String realName;

    /** 头像文件ID，前端据此调 /api/file/{fileId}/preview 展示 */
    private Long avatarFileId;

    private String mobile;

    private String email;

    private Integer gender;

    private String profile;

    private String deptName;

    private Integer status;

    private LocalDateTime createTime;
}
