package com.sched.entity.dto.vo;

import com.sched.enmu.ResultValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回结构。业务代码一律通过 success / fail 静态方法构造，
 * 不要在 Controller 里重复 builder()。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(ResultValue.SUCCESS.getCode())
                .msg(ResultValue.SUCCESS.getMsg())
                .data(data)
                .build();
    }

    /** 成功（无数据） */
    public static Result<Void> success() {
        return Result.<Void>success(null);
    }

    /** 失败：code 与文案均由调用方指定，文案必须是可展示给用户的 */
    public static <T> Result<T> fail(Integer code, String msg) {
        return Result.<T>builder()
                .code(code)
                .msg(msg)
                .build();
    }

    /** 失败：使用枚举的状态码与默认文案 */
    public static <T> Result<T> fail(ResultValue resultValue) {
        return fail(resultValue.getCode(), resultValue.getMsg());
    }
}
