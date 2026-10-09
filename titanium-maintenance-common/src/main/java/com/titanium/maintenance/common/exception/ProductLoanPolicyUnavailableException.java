package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 保单贷款策略不可得（g04-03）。
 * <p>
 * 产品域<b>正常应答</b>但业务码失败（该业务时点无已发布贷款策略，下游失败关闭语义），或应答体
 * 无效——可贷比例与年利率不可得，保全域贷款案件一律失败关闭，禁止以默认比例/利率兜底。
 * 与「产品域服务不可达」（{@link MaintenanceRemoteCallException}，Web 层映射 HTTP 502）严格区分。
 * </p>
 */
public class ProductLoanPolicyUnavailableException extends DomainException {

    private static final MaintenanceErrorCode ERROR_CODE = MaintenanceErrorCode.MAINTENANCE_LOAN_POLICY_UNAVAILABLE;

    public ProductLoanPolicyUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public ProductLoanPolicyUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
