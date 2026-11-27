package com.sched.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改用户资料入参。
 * 刻意不包含 username / password / status 等字段，避免通过资料接口提权或改密。
 */
@Data
public class UserUpdateDTO {

    @Size(max = 64, message = "昵称最长 64 个字符")
    private String nickname;

    @Size(max = 64, message = "真实姓名最长 64 个字符")
    private String realName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱最长 128 个字符")
    private String email;

    @Min(value = 0, message = "性别取值 0/1/2")
    @Max(value = 2, message = "性别取值 0/1/2")
    private Integer gender;

    @Size(max = 500, message = "个人简介最长 500 个字符")
    private String profile;

    @Size(max = 128, message = "部门名称最长 128 个字符")
    private String deptName;
}
