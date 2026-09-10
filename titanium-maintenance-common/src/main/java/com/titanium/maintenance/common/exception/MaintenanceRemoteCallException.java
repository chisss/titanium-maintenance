package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.BaseErrorCode;

/**
 * 跨域远程调用失败异常。
 * <p>
 * 用于保全域调用计费/支付/产品/核保等下游服务失败（网络、超时、契约无效）的场景，
 * 由 Web 层 {@code MaintenanceExceptionHandler} 统一映射为 HTTP 502，错误码仍为
 * 71 段标准业务码（如 {@code MAINTENANCE_BILLING_REMOTE_ERROR}）。
 * </p>
 */
public class MaintenanceRemoteCallException extends BusinessException {

    public MaintenanceRemoteCallException(String message, BaseErrorCode errorCode) {
        super(message, errorCode);
    }

    /**
     * 携带根因构造：便于定位网络/超时/契约反序列化等真实故障，避免根因在适配器边界丢失。
     *
     * @param message   错误消息
     * @param errorCode 标准错误码枚举
     * @param cause     根因异常
     */
    public MaintenanceRemoteCallException(String message, BaseErrorCode errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
