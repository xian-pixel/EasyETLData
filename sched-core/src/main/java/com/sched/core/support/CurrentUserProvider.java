package com.sched.core.support;

/**
 * 当前登录用户抽象。
 * 业务代码只依赖本接口，不直接读请求头或参数，避免越权。
 * 接入 Spring Security + JWT 后，新增 SecurityCurrentUserProvider 实现即可，
 * 届时删除 DevCurrentUserProvider。
 */
public interface CurrentUserProvider {

    /**
     * 当前登录用户ID；未登录时抛出 BizException(ResultValue.NOT_LOGGED_IN)
     */
    Long currentUserId();
}
