package com.titanium.maintenance.valueobject.casecreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

class MaintenanceCaseIdempotencyKeyTest {

    @Test
    void shouldDeriveStableMaintenanceId() {
        MaintenanceCaseIdempotencyKey first = new MaintenanceCaseIdempotencyKey(
                "tenant-1", MaintenanceChannel.API, "request-1");
        MaintenanceCaseIdempotencyKey retry = new MaintenanceCaseIdempotencyKey(
                "tenant-1", MaintenanceChannel.API, "request-1");

        assertEquals(first.maintenanceId(), retry.maintenanceId());
        assertEquals(36, first.maintenanceId().id().length());
        assertEquals(first.maintenanceId().id(), UUID.fromString(first.maintenanceId().id()).toString());
    }

    @Test
    void shouldIsolateTenantAndSource() {
        MaintenanceCaseIdempotencyKey base = new MaintenanceCaseIdempotencyKey(
                "tenant-1", MaintenanceChannel.API, "request-1");
        MaintenanceCaseIdempotencyKey otherTenant = new MaintenanceCaseIdempotencyKey(
                "tenant-2", MaintenanceChannel.API, "request-1");
        MaintenanceCaseIdempotencyKey otherSource = new MaintenanceCaseIdempotencyKey(
                "tenant-1", MaintenanceChannel.MANUAL, "request-1");

        assertNotEquals(base.maintenanceId(), otherTenant.maintenanceId());
        assertNotEquals(base.maintenanceId(), otherSource.maintenanceId());
    }

    @Test
    void shouldRejectBlankRequestKey() {
        assertThrows(MaintenanceValidationException.class, () ->
                new MaintenanceCaseIdempotencyKey("tenant-1", MaintenanceChannel.API, " "));
    }
}
