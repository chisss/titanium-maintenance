package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.BaseErrorCode;

/**
 * 保全配置管理认证失败异常（HTTP 401）。
 * <p>
 * 用于未认证或缺少租户上下文的配置管理请求，由 Web 层
 * {@code MaintenanceExceptionHandler} 统一映射为 HTTP 401，错误码为
 * 71 段对应标准业务码。
 * </p>
 */
public class MaintenanceAuthenticationException extends BusinessException {

    public MaintenanceAuthenticationException(String message, BaseErrorCode errorCode) {
        super(message, errorCode);
    }
}
