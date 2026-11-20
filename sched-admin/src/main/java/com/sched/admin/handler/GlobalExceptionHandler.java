package com.sched.admin.handler;

import com.sched.entity.dto.vo.Result;
import com.sched.enmu.ResultValue;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：所有 Controller 抛出的异常统一收敛成 Result 返回。
 * 原则：校验类提示原样返回给前端；系统/数据库类异常只打日志，前端一律给通用文案，
 * 禁止把 e.getMessage() 或堆栈直接透出。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * @Valid 校验失败（RequestBody 校验 / 表单绑定）。
     * MethodArgumentNotValidException 是 BindException 的子类，这里一并覆盖。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败：{}", detail);
        return fail(ResultValue.PARAM_FORMAT_ERROR, detail.isEmpty() ? ResultValue.PARAM_ERROR.getMsg() : detail);
    }

    /** 方法参数上的校验失败（如 @RequestParam 加了 @Min / @NotBlank） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(this::formatViolation)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败：{}", detail);
        return fail(ResultValue.PARAM_FORMAT_ERROR, detail.isEmpty() ? ResultValue.PARAM_ERROR.getMsg() : detail);
    }

    /** 缺少必填的 query / form 参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数：{}", e.getParameterName());
        return fail(ResultValue.PARAM_MISSING, "缺少必填参数：" + e.getParameterName());
    }

    /** 请求方式不支持，如用 GET 调了 POST 接口 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方式不支持：{}", e.getMethod());
        return fail(ResultValue.REQUEST_METHOD_NOT_SUPPORTED, ResultValue.REQUEST_METHOD_NOT_SUPPORTED.getMsg());
    }

    /** 请求体解析失败（JSON 格式错误等），底层解析细节只进日志 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return fail(ResultValue.PARAM_FORMAT_ERROR, "请求体格式不正确");
    }

    /**
     * 路径不存在（Spring Boot 3.2 起抛 NoResourceFoundException）。
     * 不单独处理的话会被兜底成"系统繁忙"，把 404 误报成 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        log.warn("请求路径不存在：{} {}", e.getHttpMethod(), e.getResourcePath());
        return fail(ResultValue.DATA_NOT_FOUND, "请求路径不存在");
    }

    /** 数据库操作异常：日志留全栈便于排查，前端只给通用提示 */
    @ExceptionHandler(DataAccessException.class)
    public Result<Void> handleDataAccess(DataAccessException e) {
        log.error("数据库操作异常", e);
        return fail(ResultValue.DB_ERROR, ResultValue.DB_ERROR.getMsg());
    }

    /** 兜底：任何未捕获异常，统一 SYSTEM_ERROR，禁止透出异常信息 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未捕获异常", e);
        return fail(ResultValue.SYSTEM_ERROR, ResultValue.SYSTEM_ERROR.getMsg());
    }

    private Result<Void> fail(ResultValue value, String msg) {
        return Result.<Void>builder()
                .code(value.getCode())
                .msg(msg)
                .build();
    }

    private String formatFieldError(FieldError error) {
        String message = error.getDefaultMessage();
        return error.getField() + (message == null ? " 校验不通过" : "：" + message);
    }

    private String formatViolation(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + "：" + violation.getMessage();
    }
}
