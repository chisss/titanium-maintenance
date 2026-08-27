package com.titanium.maintenance.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.port.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingEvidence;
import com.titanium.maintenance.port.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingRequest;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

class ProductMaintenanceOfferingPortTest {

    @Test
    void shouldNormalizeImmutableOfferingEvidence() {
        ProductMaintenanceOfferingEvidence evidence = new ProductMaintenanceOfferingEvidence(
                " tenant-1 ", "product-1", "product-v3", "plan-v8", "offering-1", "offering-v2",
                "A".repeat(64), now(), Set.of("SURRENDER", "CONTACT_CHANGE"));

        assertEquals("tenant-1", evidence.tenantId());
        assertEquals("a".repeat(64), evidence.contentHash());
        assertThrows(UnsupportedOperationException.class, () -> evidence.allowedItemCodes().add("NEW_ITEM"));
    }

    @Test
    void shouldRequireCompleteResolutionContextAndEvidence() {
        assertThrows(RuntimeException.class, () -> new ProductMaintenanceOfferingRequest(
                "tenant-1", "product-1", "product-v3", null, PolicyStatus.EFFECTIVE,
                MaintenanceChannel.API, now()));
        assertThrows(RuntimeException.class, () -> new ProductMaintenanceOfferingEvidence(
                "tenant-1", "product-1", "product-v3", "plan-v8", "offering-1", "offering-v2",
                "invalid", now(), Set.of("CONTACT_CHANGE")));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.of(2026, 8, 24, 8, 0, 0, 0, ZoneOffset.UTC);
    }
}
