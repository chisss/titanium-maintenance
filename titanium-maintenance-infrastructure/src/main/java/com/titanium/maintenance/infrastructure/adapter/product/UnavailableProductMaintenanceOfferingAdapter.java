package com.titanium.maintenance.infrastructure.adapter.product;

import com.titanium.maintenance.common.enums.ProductMaintenanceOfferingFailureReason;
import com.titanium.maintenance.common.exception.ProductMaintenanceOfferingException;
import com.titanium.maintenance.port.product.ProductMaintenanceOfferingPort;

/** Product 正式 Offering 契约未接入时使用的失败关闭适配器。 */
public class UnavailableProductMaintenanceOfferingAdapter implements ProductMaintenanceOfferingPort {

    @Override
    public ProductMaintenanceOfferingEvidence resolve(ProductMaintenanceOfferingRequest request) {
        throw new ProductMaintenanceOfferingException(
                ProductMaintenanceOfferingFailureReason.UNAVAILABLE,
                "Product权威保全Offering API尚未接入");
    }
}
