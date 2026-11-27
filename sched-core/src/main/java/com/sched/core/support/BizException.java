package com.sched.core.support;

import com.sched.enmu.ResultValue;
import lombok.Getter;

/**
 * 业务异常：只承载"可展示给前端"的提示与状态码。
 * 系统异常一律走 GlobalExceptionHandler 的兜底分支，禁止把堆栈或 SQL 细节放进这里。
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(ResultValue resultValue) {
        super(resultValue.getMsg());
        this.code = resultValue.getCode();
    }

    public BizException(ResultValue resultValue, String msg) {
        super(msg);
        this.code = resultValue.getCode();
    }
}
