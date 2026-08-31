package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.titanium.maintenance.event.MaintenanceEffectCompensationRequiredEvent;
import com.titanium.maintenance.event.MaintenanceEffectCompensationResolvedEvent;
import com.titanium.maintenance.query.mapper.MaintenanceViewMapper;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectCompensationEvidence;

class MaintenanceEffectCompensationProjectionEventHandlerTest {

    @Test
    void shouldProjectRequiredAndResolvedCompensationState() {
        MaintenanceViewRepository repository = mock(MaintenanceViewRepository.class);
        MaintenanceProjectionEventHandler handler = new MaintenanceProjectionEventHandler(
                repository, Mappers.getMapper(MaintenanceViewMapper.class));
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("maintenance-1");
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        LocalDateTime recordedAt = LocalDateTime.parse("2026-08-25T20:10:00");
        MaintenanceEffectCompensationEvidence evidence = new MaintenanceEffectCompensationEvidence(
                "compensation-1", "request-1", "END-001", 8, "a".repeat(64),
                "案件回执写入失败", recordedAt, "operator-1");

        handler.on(new MaintenanceEffectCompensationRequiredEvent(
                MaintenanceId.of("maintenance-1"), "effect-task-1", evidence,
                recordedAt, "operator-1", "tenant-1"));

        assertTrue(view.isEffectCompensationRequired());
        org.junit.jupiter.api.Assertions.assertEquals("END-001", view.getEffectCompensationEndorsementNo());

        LocalDateTime resolvedAt = recordedAt.plusMinutes(5);
        handler.on(new MaintenanceEffectCompensationResolvedEvent(
                MaintenanceId.of("maintenance-1"), "compensation-1", "END-001",
                resolvedAt, "operator-2", "tenant-1"));

        assertFalse(view.isEffectCompensationRequired());
        org.junit.jupiter.api.Assertions.assertEquals(resolvedAt, view.getEffectCompensationResolvedAt());
        verify(repository, org.mockito.Mockito.times(2)).save(view);
    }
}
