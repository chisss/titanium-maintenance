package com.titanium.maintenance.valueobject.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

class MaintenanceRetroactiveImpactAnalysisTest {

    private static final LocalDateTime SCOPE_FROM = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime SCOPE_TO = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Test
    void shouldCompleteOnlyAfterAllAuthorityDomainsAreCovered() {
        MaintenanceRetroactiveImpactAnalysis analysis = analysis().complete(
                MaintenanceRetroactiveImpactAnalysis.requiredDomains(), List.of(blockingClaim()),
                "impact-evidence-v1", "b".repeat(64), SCOPE_TO.plusMinutes(1));

        assertEquals(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED, analysis.status());
        assertEquals(1, analysis.pendingItemCount());
        assertEquals(1, analysis.blockingItemCount());
    }

    @Test
    void shouldRejectIncompleteCoverageAndOutOfScopeItem() {
        MaintenanceRetroactiveImpactAnalysis analysis = analysis();

        assertThrows(MaintenanceValidationException.class, () -> analysis.complete(
                List.of(MaintenanceRetroactiveImpactDomain.POLICY), List.of(),
                "impact-evidence-v1", "b".repeat(64), SCOPE_TO.plusMinutes(1)));
        assertThrows(MaintenanceValidationException.class, () -> analysis.complete(
                MaintenanceRetroactiveImpactAnalysis.requiredDomains(),
                List.of(claimAt(SCOPE_FROM)), "impact-evidence-v1", "b".repeat(64),
                SCOPE_TO.plusMinutes(1)));
    }

    @Test
    void shouldTrackFailedAnalysisWithoutPartialItems() {
        MaintenanceRetroactiveImpactAnalysis failed = analysis().fail(
                "CLAIM_UNAVAILABLE", "理赔取证失败", SCOPE_TO.plusMinutes(1));

        assertEquals(MaintenanceRetroactiveImpactAnalysisStatus.FAILED, failed.status());
        assertEquals("CLAIM_UNAVAILABLE", failed.failureCode());
        assertEquals(List.of(), failed.items());
    }

    private MaintenanceRetroactiveImpactAnalysis analysis() {
        return MaintenanceRetroactiveImpactAnalysis.start(
                "analysis-1", 1, "operation-1", "a".repeat(64),
                SCOPE_FROM, SCOPE_TO, SCOPE_TO);
    }

    private MaintenanceRetroactiveImpactItem blockingClaim() {
        return claimAt(SCOPE_FROM.plusDays(1));
    }

    private MaintenanceRetroactiveImpactItem claimAt(LocalDateTime occurredAt) {
        return new MaintenanceRetroactiveImpactItem(
                "CLAIM:claim-1", MaintenanceRetroactiveImpactDomain.CLAIM,
                MaintenanceRetroactiveImpactType.CLAIM, "claim-1", "CL-1", occurredAt,
                "PAID", new BigDecimal("1000.00"), "CNY",
                MaintenanceRetroactiveImpactSeverity.BLOCKING,
                MaintenanceRetroactiveImpactItemStatus.PENDING,
                "追溯期间存在已赔付理赔", "claim-view-v1", "c".repeat(64));
    }
}
