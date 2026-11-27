package com.sched.core.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地开发用的当前用户实现：从配置 sched.security.dev-user-id 读取固定用户ID。
 * 只在配置了该属性时生效；接入 Spring Security + JWT 后，
 * 请删除本类并在 application.yml 中移除 sched.security.dev-user-id，
 * 改用从 SecurityContext 取用户的实现。
 */
@Component
@ConditionalOnProperty(name = "sched.security.dev-user-id")
public class DevCurrentUserProvider implements CurrentUserProvider {

    private final Long userId;

    public DevCurrentUserProvider(@Value("${sched.security.dev-user-id}") Long userId) {
        this.userId = userId;
    }

    @Override
    public Long currentUserId() {
        return userId;
    }
}
