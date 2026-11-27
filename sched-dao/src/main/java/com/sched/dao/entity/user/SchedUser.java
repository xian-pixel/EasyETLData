package com.sched.dao.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台用户实体，对应 t_sched_user。
 * 注意：本类含 password 等敏感字段，禁止直接作为接口返回值，统一转 UserProfileVO。
 */
@Data
@TableName("t_sched_user")
public class SchedUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID（雪花，由 MyBatis-Plus ASSIGN_ID 生成） */
    @TableId(value = "user_id", type = IdType.ASSIGN_ID)
    private Long userId;

    /** 登录名，唯一（唯一键为 username + deleted） */
    private String username;

    /** BCrypt 哈希，禁止明文/MD5；序列化时忽略，防止误返回前端 */
    @JsonIgnore
    private String password;

    /** 昵称 */
    private String nickname;

    /** 真实姓名 */
    private String realName;

    /** 头像文件ID，关联 t_sched_file.file_id，不存二进制 */
    private Long avatarFileId;

    /** 手机号（如需合规，可改存密文并另加 mobile_hash 检索列） */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 性别：0 未知，1 男，2 女 */
    private Integer gender;

    /** 个人简介 */
    private String profile;

    /** 所属部门（展示用冗余字段） */
    private String deptName;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建人ID */
    private Long createBy;

    /** 创建时间，由数据库默认值维护，插入时不赋值 */
    private LocalDateTime createTime;

    /** 更新人ID */
    private Long updateBy;

    /** 更新时间，由数据库 ON UPDATE 维护 */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：0 未删除，非 0 为删除时间戳。
     * 刻意不使用 @TableLogic：其 delval 只支持常量，而本方案要求写入删除时间戳，
     * 统一在 Service 层手动 UPDATE。
     */
    private Long deleted;

    /** 乐观锁版本号；需注册 OptimisticLockerInnerInterceptor 才生效，否则仅作展示字段 */
    @Version
    private Integer version;
}
