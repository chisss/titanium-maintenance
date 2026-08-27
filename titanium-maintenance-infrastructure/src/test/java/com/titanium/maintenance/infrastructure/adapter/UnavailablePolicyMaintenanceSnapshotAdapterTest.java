package com.titanium.maintenance.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.PolicyMaintenanceSnapshotFailureReason;
import com.titanium.maintenance.common.exception.PolicyMaintenanceSnapshotException;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;

class UnavailablePolicyMaintenanceSnapshotAdapterTest {

    @Test
    void shouldFailClosedUntilPolicyPublishesAuthoritativeSnapshotContract() {
        UnavailablePolicyMaintenanceSnapshotAdapter adapter =
                new UnavailablePolicyMaintenanceSnapshotAdapter();

        PolicyMaintenanceSnapshotException exception = assertThrows(
                PolicyMaintenanceSnapshotException.class,
                () -> adapter.capture(new PolicyMaintenanceSnapshotRequest("policy-1", "tenant-1")));

        assertEquals(PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE, exception.getReason());
    }
}
