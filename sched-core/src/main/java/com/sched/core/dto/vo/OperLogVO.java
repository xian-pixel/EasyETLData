package com.sched.core.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志对外视图。
 */
@Data
public class OperLogVO {

    private Long logId;

    private Long userId;

    private String username;

    private String module;

    private String action;

    private String method;

    private String requestMethod;

    private String requestUri;

    private String requestParams;

    /**
     * 1 成功，0 失败
     */
    private Integer resultStatus;

    private String errorMsg;

    private String ip;

    private Long costMs;

    private LocalDateTime createTime;
}
