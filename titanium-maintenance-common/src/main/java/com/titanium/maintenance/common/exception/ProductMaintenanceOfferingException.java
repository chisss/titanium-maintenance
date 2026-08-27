package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.enums.ProductMaintenanceOfferingFailureReason;
import com.titanium.metadata.exception.DomainException;

import lombok.Getter;

/** Product 保全 Offering 不存在、不匹配或权威服务不可用。 */
@Getter
public class ProductMaintenanceOfferingException extends DomainException {

    private static final String ERROR_CODE_PREFIX = "MAINTENANCE_PRODUCT_OFFERING_";

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

    private static String errorCode(ProductMaintenanceOfferingFailureReason reason) {
        return ERROR_CODE_PREFIX + requireReason(reason).name();
    }

    private static ProductMaintenanceOfferingFailureReason requireReason(
            ProductMaintenanceOfferingFailureReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Product保全Offering失败原因不能为空");
        }
        return reason;
    }
}
