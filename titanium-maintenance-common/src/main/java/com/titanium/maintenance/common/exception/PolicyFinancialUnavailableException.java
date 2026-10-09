package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 保单财务取数不可得（g04-02）。
 * <p>
 * 保单域<b>正常应答</b>但业务码失败（保单不存在或财务要素缺失，下游失败关闭语义），或应答体
 * 无效——财务要素不可得，保全域相关动作（退费/贷款/减额缴清）一律失败关闭，禁止以 0 兜底。
 * 与「保单域服务不可达」（{@link MaintenanceRemoteCallException}，
 * {@code MAINTENANCE_POLICY_REMOTE_ERROR}，Web 层映射 HTTP 502）严格区分。
 * </p>
 */
public class PolicyFinancialUnavailableException extends DomainException {

    private static final MaintenanceErrorCode ERROR_CODE = MaintenanceErrorCode.MAINTENANCE_POLICY_FINANCIAL_UNAVAILABLE;

    public PolicyFinancialUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public PolicyFinancialUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
