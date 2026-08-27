package com.titanium.maintenance.valueobject.casecreation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

/**
 * Regression: API-05 同载荷建案重试被快照采集时间误判为不同基准。
 * Found by /qa on 2026-08-26
 * Report: .gstack/qa-reports/qa-report-maintenance-fullstack-2026-08-26.md
 */
class PolicyMaintenanceSnapshotIdempotencyRegressionTest {

    @Test
    void shouldTreatSamePolicyVersionWithDifferentCaptureTimeAsSameBaseline() {
        OffsetDateTime businessTime = OffsetDateTime.of(2026, 8, 26, 0, 0, 0, 0, ZoneOffset.ofHours(8));
        Map<String, MaintenanceFieldValue> fields = Map.of(
                "policy.holder.mobile", MaintenanceFieldValue.text("13800138000"));

        PolicyMaintenanceSnapshot first = snapshot(businessTime, businessTime.plusSeconds(1), fields);
        PolicyMaintenanceSnapshot retry = snapshot(businessTime, businessTime.plusSeconds(2), fields);

        assertTrue(first.sameBusinessBaseline(retry));
    }

    private PolicyMaintenanceSnapshot snapshot(
            OffsetDateTime businessTime,
            OffsetDateTime capturedAt,
            Map<String, MaintenanceFieldValue> fields) {
        return new PolicyMaintenanceSnapshot(
                "1", PolicyId.of("policy-1"), "POL-1", CustomerId.of("customer-1"),
                "product-1", "V1.0", "PLAN-1", PolicyStatus.EFFECTIVE, 0,
                businessTime, null, null,
                new MaintenanceSnapshotReference(
                        "axon-event://policy/1/policy-1?version=0", "a".repeat(64), 0, capturedAt),
                fields);
    }
}
