package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 保单贷款放款不可得（g04-03）。
 * <p>
 * 保单域<b>正常应答</b>但业务码失败（保单不存在/未生效/金额利率非法/重复放款，下游失败关闭语义），
 * 或应答体无效——放款事实未建立，保全域贷款案件一律失败关闭，禁止以 0 或空回执兜底。
 * 与「保单域服务不可达」（{@link MaintenanceRemoteCallException}，Web 层映射 HTTP 502）严格区分。
 * </p>
 */
public class PolicyLoanUnavailableException extends DomainException {

    private static final MaintenanceErrorCode ERROR_CODE = MaintenanceErrorCode.MAINTENANCE_LOAN_DISBURSEMENT_UNAVAILABLE;

    public PolicyLoanUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public PolicyLoanUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
