package com.titanium.maintenance.valueobject.casecreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

class PolicyMaintenanceSnapshotTest {

    private static final OffsetDateTime CAPTURED_AT =
            OffsetDateTime.of(2026, 8, 24, 11, 30, 0, 0, ZoneOffset.ofHours(8));

    @Test
    void shouldDefensivelyFreezeAndSortStructuredFields() {
        Map<String, MaintenanceFieldValue> fields = new LinkedHashMap<>();
        fields.put("policy.holder.mobile", MaintenanceFieldValue.text("13800000000"));
        fields.put("policy.address.city", MaintenanceFieldValue.text("上海"));

        PolicyMaintenanceSnapshot snapshot = snapshot(7L, reference(7L), fields);
        fields.clear();

        assertEquals(
                List.of("policy.address.city", "policy.holder.mobile"),
                List.copyOf(snapshot.fieldValues().keySet()));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.fieldValues().put("policy.number", MaintenanceFieldValue.text("P001")));
    }

    @Test
    void shouldRejectSnapshotReferenceForAnotherPolicyVersion() {
        assertThrows(MaintenanceValidationException.class,
                () -> snapshot(7L, reference(8L), validFields()));
    }

    @Test
    void shouldRejectMissingVersionEvidenceAndStructuredFields() {
        assertThrows(MaintenanceValidationException.class,
                () -> new PolicyMaintenanceSnapshot(
                        "tenant-1", PolicyId.of("policy-1"), "P202608240001",
                        CustomerId.of("customer-1"), "product-1", " ", "plan-v2",
                        PolicyStatus.EFFECTIVE, 7L, CAPTURED_AT, reference(7L), validFields()));
        assertThrows(MaintenanceValidationException.class,
                () -> snapshot(7L, reference(7L), Map.of()));
    }

    @Test
    void shouldOnlyTreatEffectivePolicyAsActive() {
        PolicyMaintenanceSnapshot snapshot = new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P202608240001", CustomerId.of("customer-1"),
                "product-1", "product-v3", "plan-v2", PolicyStatus.SUSPENDED, 7L, CAPTURED_AT,
                reference(7L), validFields());

        assertFalse(snapshot.active());
    }

    private PolicyMaintenanceSnapshot snapshot(
            long policyVersion,
            MaintenanceSnapshotReference reference,
            Map<String, MaintenanceFieldValue> fields) {
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P202608240001", CustomerId.of("customer-1"),
                "product-1", "product-v3", "plan-v2", PolicyStatus.EFFECTIVE, policyVersion,
                CAPTURED_AT, reference, fields);
    }

    private MaintenanceSnapshotReference reference(long policyVersion) {
        return new MaintenanceSnapshotReference(
                "policy/policy-1/versions/" + policyVersion,
                "a".repeat(64), policyVersion, CAPTURED_AT);
    }

    private Map<String, MaintenanceFieldValue> validFields() {
        return Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13800000000"));
    }
}
