package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 保单贷款还款收款失败（g04-04）。
 * <p>
 * 支付域<b>正常应答</b>但业务码失败，或应答体无效——还款收款单未建立，保全域贷款还款案件一律失败关闭，
 * 禁止以 0 或空指令号兜底。与「支付域服务不可达」（{@link MaintenanceRemoteCallException}，
 * {@code MAINTENANCE_PAYMENT_REMOTE_ERROR}，Web 层映射 HTTP 502）严格区分。
 * </p>
 */
public class LoanRepaymentCollectionUnavailableException extends DomainException {

    private static final MaintenanceErrorCode ERROR_CODE =
            MaintenanceErrorCode.MAINTENANCE_LOAN_REPAYMENT_COLLECTION_UNAVAILABLE;

    public LoanRepaymentCollectionUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public LoanRepaymentCollectionUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
