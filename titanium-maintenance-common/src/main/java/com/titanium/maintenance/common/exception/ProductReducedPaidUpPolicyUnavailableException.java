package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 减额缴清净费率策略不可得（g04-05）。
 * <p>
 * 产品域<b>正常应答</b>但业务码失败（该业务时点无已发布减额缴清净费率策略，下游失败关闭语义），或
 * 应答体无效——每元保额趸缴净费率不可得，保全域减额缴清案件一律失败关闭，禁止以近似费率或默认费率
 * 兜底（费率错一位即新保额错一位，属精算口径错误）。与「产品域服务不可达」
 * （{@link MaintenanceRemoteCallException}，Web 层映射 HTTP 502）严格区分。
 * </p>
 */
public class ProductReducedPaidUpPolicyUnavailableException extends DomainException {

    private static final MaintenanceErrorCode ERROR_CODE =
            MaintenanceErrorCode.MAINTENANCE_REDUCED_PAID_UP_POLICY_UNAVAILABLE;

    public ProductReducedPaidUpPolicyUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public ProductReducedPaidUpPolicyUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
