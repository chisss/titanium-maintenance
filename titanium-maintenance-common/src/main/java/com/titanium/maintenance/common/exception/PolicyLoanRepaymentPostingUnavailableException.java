package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 保单贷款还款冲销入账失败（g04-04）。
 * <p>
 * 计费域<b>正常应答</b>但业务码失败，或应答体无效——还款冲销入账事实未建立，保全域贷款还款案件一律失败关闭，
 * 禁止以 0 或空流水号兜底。与「计费域服务不可达」（{@link MaintenanceRemoteCallException}，
 * {@code MAINTENANCE_BILLING_REMOTE_ERROR}，Web 层映射 HTTP 502）严格区分。
 * </p>
 */
public class PolicyLoanRepaymentPostingUnavailableException extends DomainException {

    private static final MaintenanceErrorCode ERROR_CODE =
            MaintenanceErrorCode.MAINTENANCE_LOAN_REPAYMENT_POSTING_UNAVAILABLE;

    public PolicyLoanRepaymentPostingUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public PolicyLoanRepaymentPostingUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
