package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.BaseErrorCode;

/**
 * 保全配置管理授权失败异常（HTTP 403）。
 * <p>
 * 用于已认证但缺少操作权限的配置管理请求，由 Web 层
 * {@code MaintenanceExceptionHandler} 统一映射为 HTTP 403，错误码为
 * 71 段对应标准业务码。
 * </p>
 */
public class MaintenanceForbiddenException extends BusinessException {

    public MaintenanceForbiddenException(String message, BaseErrorCode errorCode) {
        super(message, errorCode);
    }
}
