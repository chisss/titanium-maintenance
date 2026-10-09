package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 保单贷款还款失败（g04-04）。
 * <p>
 * 保单域<b>正常应答</b>但业务码失败（贷款不存在/还款额超过欠款总额/要素非法），或应答体无效——还款记账未成立，
 * 保全域贷款还款案件一律失败关闭，禁止以 0 或空回执兜底。与「保单域服务不可达」
 * （{@link MaintenanceRemoteCallException}，{@code MAINTENANCE_POLICY_REMOTE_ERROR}，Web 层映射 HTTP 502）严格区分。
 * </p>
 */
public class PolicyLoanRepaymentUnavailableException extends DomainException {

    private static final MaintenanceErrorCode ERROR_CODE = MaintenanceErrorCode.MAINTENANCE_LOAN_REPAYMENT_UNAVAILABLE;

    public PolicyLoanRepaymentUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public PolicyLoanRepaymentUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
