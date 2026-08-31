package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.enums.ProductMaintenanceOfferingFailureReason;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

import lombok.Getter;

/** Product 保全 Offering 不存在、不匹配或权威服务不可用。 */
@Getter
public class ProductMaintenanceOfferingException extends DomainException {

    private final ProductMaintenanceOfferingFailureReason reason;

    public ProductMaintenanceOfferingException(
            ProductMaintenanceOfferingFailureReason reason,
            String message) {
        super(errorCode(reason), message);
        this.reason = requireReason(reason);
    }

    public ProductMaintenanceOfferingException(
            ProductMaintenanceOfferingFailureReason reason,
            String message,
            Throwable cause) {
        super(errorCode(reason), message, cause);
        this.reason = requireReason(reason);
    }

    /** 失败原因 → 标准错误码枚举（71 段）映射。 */
    private static MaintenanceErrorCode errorCode(ProductMaintenanceOfferingFailureReason reason) {
        return switch (requireReason(reason)) {
            case NOT_FOUND -> MaintenanceErrorCode.MAINTENANCE_PRODUCT_OFFERING_NOT_FOUND;
            case VERSION_MISMATCH -> MaintenanceErrorCode.MAINTENANCE_PRODUCT_OFFERING_VERSION_MISMATCH;
            case NOT_APPLICABLE -> MaintenanceErrorCode.MAINTENANCE_PRODUCT_OFFERING_NOT_APPLICABLE;
            case CONTRACT_INVALID -> MaintenanceErrorCode.MAINTENANCE_PRODUCT_OFFERING_CONTRACT_INVALID;
            case UNAVAILABLE -> MaintenanceErrorCode.MAINTENANCE_PRODUCT_OFFERING_UNAVAILABLE;
        };
    }

    private static ProductMaintenanceOfferingFailureReason requireReason(
            ProductMaintenanceOfferingFailureReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Product保全Offering失败原因不能为空");
        }
        return reason;
    }
}
