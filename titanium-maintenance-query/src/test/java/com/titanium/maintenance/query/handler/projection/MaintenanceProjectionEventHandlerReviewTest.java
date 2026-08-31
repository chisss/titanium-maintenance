package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.event.MaintenanceCaseRejectedByReviewEvent;
import com.titanium.maintenance.event.MaintenanceCaseRejectedByUnderwritingEvent;
import com.titanium.maintenance.query.mapper.MaintenanceViewMapper;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;

class MaintenanceProjectionEventHandlerReviewTest {

    @Test
    void shouldProjectReviewRejectionAsTerminalCaseStatus() {
        MaintenanceViewRepository repository = mock(MaintenanceViewRepository.class);
        MaintenanceProjectionEventHandler handler = new MaintenanceProjectionEventHandler(
                repository, Mappers.getMapper(MaintenanceViewMapper.class));
        MaintenanceView view = new MaintenanceView();
        when(repository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        LocalDateTime rejectedAt = LocalDateTime.parse("2026-08-25T11:30:00");

        handler.on(new MaintenanceCaseRejectedByReviewEvent(
                MaintenanceId.of("case-1"), "task-review", "a".repeat(64),
                "APPROVAL_STANDARD", "policy-v1", "材料不一致",
                rejectedAt, "reviewer-1", "tenant-1"));

        assertEquals(MaintenanceStatus.REJECTED, view.getStatus());
        assertEquals("reviewer-1", view.getUpdatedBy());
        assertEquals(rejectedAt, view.getUpdateTime());
        verify(repository).save(view);
    }

    @Test
    void shouldProjectUnderwritingRejectionAsTerminalCaseStatus() {
        MaintenanceViewRepository repository = mock(MaintenanceViewRepository.class);
        MaintenanceProjectionEventHandler handler = new MaintenanceProjectionEventHandler(
                repository, Mappers.getMapper(MaintenanceViewMapper.class));
        MaintenanceView view = new MaintenanceView();
        when(repository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        LocalDateTime rejectedAt = LocalDateTime.parse("2026-08-25T15:30:00");

        handler.on(new MaintenanceCaseRejectedByUnderwritingEvent(
                MaintenanceId.of("case-1"), "task-underwriting", "MUW-1", "b".repeat(64),
                "rule-v1", "model-v1", "风险规则拒绝",
                rejectedAt, "maintenance-service", "tenant-1"));

        assertEquals(MaintenanceStatus.REJECTED, view.getStatus());
        assertEquals("maintenance-service", view.getUpdatedBy());
        assertEquals(rejectedAt, view.getUpdateTime());
        verify(repository).save(view);
    }
}
