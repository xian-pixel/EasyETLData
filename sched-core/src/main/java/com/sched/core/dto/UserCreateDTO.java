package com.sched.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增用户入参。密码只收明文，服务层立刻用 BCrypt 加密，禁止落库明文。
 */
@Data
public class UserCreateDTO {

    @NotBlank(message = "登录名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,64}$", message = "登录名只能包含字母、数字、下划线，长度 4-64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
    private String password;

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
