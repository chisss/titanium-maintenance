package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.BaseErrorCode;

/**
 * 保全结算类请求与案件状态冲突异常（HTTP 409）。
 * <p>
 * 用于退保/冲正/结算入口与案件类型不匹配、检查点不完整、原确认计算不一致等冲突场景，
 * 由 Web 层 {@code MaintenanceExceptionHandler} 统一映射为 HTTP 409，错误码为
 * 71 段对应标准业务码。
 * </p>
 */
public class MaintenanceSettlementConflictException extends BusinessException {

    public MaintenanceSettlementConflictException(String message, BaseErrorCode errorCode) {
        super(message, errorCode);
    }
}
