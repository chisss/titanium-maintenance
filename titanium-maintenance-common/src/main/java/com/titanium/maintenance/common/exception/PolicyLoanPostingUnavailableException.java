package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 保单贷款入账失败（g04-03）。
 * <p>
 * 计费域<b>正常应答</b>但业务码失败，或应答体无效——贷款负债未入账，保全域贷款案件一律失败关闭，
 * 禁止以 0 或空流水号兜底。与「计费域服务不可达」（{@link MaintenanceRemoteCallException}，
 * {@code MAINTENANCE_BILLING_REMOTE_ERROR}，Web 层映射 HTTP 502）严格区分。
 * </p>
 */
public class PolicyLoanPostingUnavailableException extends DomainException {

    private static final MaintenanceErrorCode ERROR_CODE = MaintenanceErrorCode.MAINTENANCE_LOAN_POSTING_UNAVAILABLE;

    public PolicyLoanPostingUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public PolicyLoanPostingUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
