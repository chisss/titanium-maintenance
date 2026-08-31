package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.BaseErrorCode;

import lombok.Getter;

/**
 * 保全业务异常基类，仅携带 {@link BaseErrorCode} 业务错误码。
 * <p>
 * 错误码统一使用 {@link BaseErrorCode} 枚举（如 {@code MaintenanceErrorCode}），
 * 禁止使用裸字符串错误码构造。HTTP 状态码不属于领域异常职责，由 Web 层
 * {@code MaintenanceExceptionHandler} 按异常类型在传输层映射（红线 8.2）。
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    /**
     * 使用标准错误码枚举构造（首选）。
     *
     * @param message   错误消息
     * @param errorCode 标准错误码枚举
     */
    public BusinessException(String message, BaseErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode.getCode();
    }

    /**
     * 使用标准错误码枚举构造，携带根因。
     *
     * @param message   错误消息
     * @param errorCode 标准错误码枚举
     * @param cause     根因异常
     */
    public BusinessException(String message, BaseErrorCode errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode.getCode();
    }

    /**
     * 获取业务错误码（8 位数字，非 HTTP 状态码）。
     *
     * @return 业务错误码
     */
    public String getErrorCode() {
        return errorCode;
    }
}
