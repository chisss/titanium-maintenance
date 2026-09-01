package com.titanium.maintenance.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.ProductMaintenanceOfferingFailureReason;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.ProductMaintenanceOfferingException;
import com.titanium.maintenance.port.product.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingRequest;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

class UnavailableProductMaintenanceOfferingAdapterTest {

    @Test
    void shouldFailClosedBeforeProductOfferingApiIsAvailable() {
        ProductMaintenanceOfferingRequest request = new ProductMaintenanceOfferingRequest(
                "tenant-1", "product-1", "product-v3", "plan-v8", PolicyStatus.EFFECTIVE,
                MaintenanceChannel.API, OffsetDateTime.parse("2026-08-24T16:00:00+08:00"));

        ProductMaintenanceOfferingException exception = assertThrows(
                ProductMaintenanceOfferingException.class,
                () -> new UnavailableProductMaintenanceOfferingAdapter().resolve(request));

        assertEquals(ProductMaintenanceOfferingFailureReason.UNAVAILABLE, exception.getReason());
    }
}
