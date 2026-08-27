package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisFailedEvent;
import com.titanium.maintenance.query.repository.MaintenanceRetroactiveImpactItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceRetroactiveImpactItemView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;

class MaintenanceRetroactiveImpactProjectionEventHandlerTest {

    @Test
    void shouldProjectCompletedSummaryAndReplaceStructuredItems() {
        MaintenanceViewRepository caseRepository = mock(MaintenanceViewRepository.class);
        MaintenanceRetroactiveImpactItemViewRepository itemRepository =
                mock(MaintenanceRetroactiveImpactItemViewRepository.class);
        MaintenanceView view = new MaintenanceView();
        when(caseRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        MaintenanceRetroactiveImpactProjectionEventHandler handler =
                new MaintenanceRetroactiveImpactProjectionEventHandler(caseRepository, itemRepository);
        MaintenanceRetroactiveImpactAnalysis analysis = completedAnalysis();

        handler.on(new MaintenanceRetroactiveImpactAnalysisCompletedEvent(
                MaintenanceId.of("case-1"), analysis, "operator-1", "tenant-1"));

        assertEquals(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED,
                view.getRetroactiveImpactStatus());
        assertEquals("BILLING,CLAIM,PAYMENT,POLICY", view.getRetroactiveImpactCoveredDomains());
        assertEquals(1, view.getRetroactiveImpactBlockingCount());
        verify(caseRepository).save(view);
        verify(itemRepository).deleteByTenantIdAndMaintenanceIdAndAnalysisId(
                "tenant-1", "case-1", "analysis-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<MaintenanceRetroactiveImpactItemView>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(itemRepository).saveAll(captor.capture());
        MaintenanceRetroactiveImpactItemView item = captor.getValue().iterator().next();
        assertEquals("analysis-1|POLICY:END-001", item.getImpactRecordId());
        assertEquals("tenant-1", item.getTenantId());
        assertEquals("END-001", item.getReferenceNumber());
    }

    @Test
    void shouldProjectFailureWithoutReplacingImpactItems() {
        MaintenanceViewRepository caseRepository = mock(MaintenanceViewRepository.class);
        MaintenanceRetroactiveImpactItemViewRepository itemRepository =
                mock(MaintenanceRetroactiveImpactItemViewRepository.class);
        MaintenanceView view = new MaintenanceView();
        when(caseRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        MaintenanceRetroactiveImpactProjectionEventHandler handler =
                new MaintenanceRetroactiveImpactProjectionEventHandler(caseRepository, itemRepository);
        LocalDateTime startedAt = LocalDateTime.parse("2026-08-25T18:00:00");
        MaintenanceRetroactiveImpactAnalysis analysis = MaintenanceRetroactiveImpactAnalysis.start(
                        "analysis-1", 1, "operation-1", "a".repeat(64),
                        LocalDateTime.parse("2026-08-01T00:00:00"),
                        LocalDateTime.parse("2026-08-25T17:59:00"), startedAt)
                .fail("SOURCE_FAILED", "Policy不可用", startedAt.plusMinutes(1));

        handler.on(new MaintenanceRetroactiveImpactAnalysisFailedEvent(
                MaintenanceId.of("case-1"), analysis, "operator-1", "tenant-1"));

        assertEquals(MaintenanceRetroactiveImpactAnalysisStatus.FAILED,
                view.getRetroactiveImpactStatus());
        assertEquals("SOURCE_FAILED", view.getRetroactiveImpactFailureCode());
        verify(itemRepository, never()).deleteByTenantIdAndMaintenanceIdAndAnalysisId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(itemRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    private MaintenanceRetroactiveImpactAnalysis completedAnalysis() {
        LocalDateTime startedAt = LocalDateTime.parse("2026-08-25T18:00:00");
        List<MaintenanceRetroactiveImpactDomain> domains = List.of(
                MaintenanceRetroactiveImpactDomain.BILLING,
                MaintenanceRetroactiveImpactDomain.CLAIM,
                MaintenanceRetroactiveImpactDomain.PAYMENT,
                MaintenanceRetroactiveImpactDomain.POLICY);
        MaintenanceRetroactiveImpactItem item = new MaintenanceRetroactiveImpactItem(
                "POLICY:END-001", MaintenanceRetroactiveImpactDomain.POLICY,
                MaintenanceRetroactiveImpactType.SUBSEQUENT_ENDORSEMENT,
                "END-001", "END-001", LocalDateTime.parse("2026-08-20T10:00:00"),
                "ENDORSED", null, null, MaintenanceRetroactiveImpactSeverity.BLOCKING,
                MaintenanceRetroactiveImpactItemStatus.PENDING, "追溯时点后存在已落地批单",
                "POLICY_ENDORSEMENT_V1", "b".repeat(64));
        return MaintenanceRetroactiveImpactAnalysis.start(
                        "analysis-1", 1, "operation-1", "a".repeat(64),
                        LocalDateTime.parse("2026-08-01T00:00:00"),
                        LocalDateTime.parse("2026-08-25T17:59:00"), startedAt)
                .complete(domains, List.of(item), "evidence-v1", "c".repeat(64),
                        startedAt.plusMinutes(1));
    }
}
