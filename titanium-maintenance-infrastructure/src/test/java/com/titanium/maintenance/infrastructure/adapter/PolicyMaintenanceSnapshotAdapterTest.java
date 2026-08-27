package com.titanium.maintenance.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.PolicyMaintenanceSnapshotFailureReason;
import com.titanium.maintenance.common.exception.PolicyMaintenanceSnapshotException;
import com.titanium.maintenance.infrastructure.client.PolicyServiceClient;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.PolicyMaintenanceSnapshotResponse;
import com.titanium.policy.api.response.PolicySnapshotFieldValueResponse;

class PolicyMaintenanceSnapshotAdapterTest {

    @Test
    void shouldCaptureFormalPolicySnapshot() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        when(client.getMaintenanceSnapshot("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(response("tenant-1", 7L, "TEXT", "13800000000")));

        var snapshot = new PolicyMaintenanceSnapshotAdapter(client)
                .capture(new PolicyMaintenanceSnapshotRequest("policy-1", "tenant-1"));

        assertEquals(7L, snapshot.policyVersion());
        assertEquals("product-v3", snapshot.productVersion());
        assertEquals("plan-v8", snapshot.planVersion());
        assertEquals("13800000000", snapshot.fieldValues().get("policy.holder.mobile").canonicalValue());
    }

    @Test
    void shouldPreserveSuspendedStatusForProductOfferingDecision() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        when(client.getMaintenanceSnapshot("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(response(
                        "tenant-1", 8L, "TEXT", "13800000000", PolicyStatus.SUSPENDED)));

        var snapshot = new PolicyMaintenanceSnapshotAdapter(client)
                .capture(new PolicyMaintenanceSnapshotRequest("policy-1", "tenant-1"));

        assertEquals(PolicyStatus.SUSPENDED, snapshot.policyStatus());
        assertEquals(8L, snapshot.policyVersion());
    }

    @Test
    void shouldNormalizeCollectionFieldWithStableObjectId() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        OffsetDateTime capturedAt = OffsetDateTime.of(2026, 8, 24, 8, 0, 0, 0, ZoneOffset.UTC);
        PolicyMaintenanceSnapshotResponse response = new PolicyMaintenanceSnapshotResponse(
                "tenant-1", "policy-1", "P202608240001", "customer-1", "product-1", "product-v3",
                "plan-v8", PolicyStatus.EFFECTIVE, 7L, capturedAt,
                "axon-event://policy/tenant-1/policy-1?version=7", "a".repeat(64), capturedAt,
                Map.of("policy.coverage.sumInsured",
                        new PolicySnapshotFieldValueResponse("DECIMAL", "100000", "policy-product-1")));
        when(client.getMaintenanceSnapshot("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(response));

        var snapshot = new PolicyMaintenanceSnapshotAdapter(client)
                .capture(new PolicyMaintenanceSnapshotRequest("policy-1", "tenant-1"));

        assertEquals("100000", snapshot.fieldValues()
                .get("policy-product-1:policy.coverage.sumInsured").canonicalValue());
    }

    @Test
    void shouldRejectCrossTenantSnapshot() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        when(client.getMaintenanceSnapshot("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(response("tenant-2", 7L, "TEXT", "13800000000")));

        PolicyMaintenanceSnapshotException exception = assertThrows(
                PolicyMaintenanceSnapshotException.class,
                () -> new PolicyMaintenanceSnapshotAdapter(client)
                        .capture(new PolicyMaintenanceSnapshotRequest("policy-1", "tenant-1")));

        assertEquals(PolicyMaintenanceSnapshotFailureReason.TENANT_MISMATCH, exception.getReason());
    }

    @Test
    void shouldRejectMissingVersionAndInvalidFieldType() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        PolicyMaintenanceSnapshotAdapter adapter = new PolicyMaintenanceSnapshotAdapter(client);
        when(client.getMaintenanceSnapshot("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(response("tenant-1", null, "TEXT", "value")));

        PolicyMaintenanceSnapshotException missingVersion = assertThrows(
                PolicyMaintenanceSnapshotException.class,
                () -> adapter.capture(new PolicyMaintenanceSnapshotRequest("policy-1", "tenant-1")));

        assertEquals(PolicyMaintenanceSnapshotFailureReason.VERSION_MISSING, missingVersion.getReason());

        when(client.getMaintenanceSnapshot("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(response("tenant-1", 7L, "UNKNOWN", "value")));
        PolicyMaintenanceSnapshotException invalidType = assertThrows(
                PolicyMaintenanceSnapshotException.class,
                () -> adapter.capture(new PolicyMaintenanceSnapshotRequest("policy-1", "tenant-1")));

        assertEquals(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID, invalidType.getReason());
    }

    private PolicyMaintenanceSnapshotResponse response(
            String tenantId,
            Long policyVersion,
            String dataType,
            String canonicalValue) {
        return response(tenantId, policyVersion, dataType, canonicalValue, PolicyStatus.EFFECTIVE);
    }

    private PolicyMaintenanceSnapshotResponse response(
            String tenantId,
            Long policyVersion,
            String dataType,
            String canonicalValue,
            PolicyStatus policyStatus) {
        OffsetDateTime capturedAt = OffsetDateTime.of(2026, 8, 24, 8, 0, 0, 0, ZoneOffset.UTC);
        return new PolicyMaintenanceSnapshotResponse(
                tenantId, "policy-1", "P202608240001", "customer-1", "product-1", "product-v3",
                "plan-v8", policyStatus, policyVersion, capturedAt,
                "axon-event://policy/tenant-1/policy-1?version=7", "a".repeat(64), capturedAt,
                Map.of("policy.holder.mobile", new PolicySnapshotFieldValueResponse(dataType, canonicalValue)));
    }
}
