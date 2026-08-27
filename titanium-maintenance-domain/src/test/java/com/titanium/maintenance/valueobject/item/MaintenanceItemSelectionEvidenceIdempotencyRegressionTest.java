package com.titanium.maintenance.valueobject.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/**
 * Regression: API-05 同载荷重试被 Product Offering 解析时间误判为不同冻结项目。
 * Found by /qa on 2026-08-26
 * Report: .gstack/qa-reports/qa-report-maintenance-fullstack-2026-08-26.md
 */
class MaintenanceItemSelectionEvidenceIdempotencyRegressionTest {

    @Test
    void shouldIgnoreResolutionTimeWhenAuthorityEvidenceIsUnchanged() {
        MaintenanceItemSelectionEvidence first = evidence("2026-08-26T09:00:00Z");
        MaintenanceItemSelectionEvidence retry = evidence("2026-08-26T09:00:01Z");

        assertTrue(first.sameAuthoritativeSelection(retry));
    }

    private MaintenanceItemSelectionEvidence evidence(String resolvedAt) {
        return MaintenanceItemSelectionEvidence.authoritative(
                "configuration-1", "V1", "a".repeat(64),
                "offering-1", "V1", "b".repeat(64), OffsetDateTime.parse(resolvedAt));
    }
}
