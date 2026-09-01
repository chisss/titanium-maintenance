package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.port.maintenance.MaintenanceConfigurationReferencePort.ReferenceValidationRequest;

class LocalMaintenanceConfigurationReferenceAdapterTest {

    private final LocalMaintenanceConfigurationReferenceAdapter adapter =
            new LocalMaintenanceConfigurationReferenceAdapter();

    @Test
    void shouldResolveOnlyKnownLocalReferences() {
        var evidence = adapter.validate(new ReferenceValidationRequest(
                "tenant-1",
                Set.of("APPROVAL_STANDARD", "UNKNOWN_RULE"),
                Set.of("maintenance:item:operate", "unknown:permission"),
                Set.of("MAINTENANCE_VOUCHER", "UNKNOWN_TEMPLATE")));

        assertTrue(evidence.authoritative());
        assertEquals("local-reference-registry-v1", evidence.evidenceVersion());
        assertEquals(Set.of("APPROVAL_STANDARD"), evidence.resolvedRuleCodes());
        assertEquals(Set.of("maintenance:item:operate"), evidence.resolvedPermissionCodes());
        assertEquals(Set.of("MAINTENANCE_VOUCHER"), evidence.resolvedTemplateCodes());
    }
}
