package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.event.MaintenanceFieldConflictResolvedEvent;
import com.titanium.maintenance.event.MaintenanceFieldConflictsRefreshedEvent;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;

class MaintenanceFieldConflictProjectionEventHandlerTest {

    private static final MaintenanceId ID = MaintenanceId.of("case-1");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-26T10:00:00+08:00");

    private MaintenanceFieldChangeViewRepository fieldRepository;
    private MaintenanceSnapshotViewRepository snapshotRepository;
    private MaintenanceFieldConflictProjectionEventHandler handler;
    private MaintenanceFieldChangeView fieldView;
    private MaintenanceSnapshotView snapshotView;

    @BeforeEach
    void setUp() {
        fieldRepository = mock(MaintenanceFieldChangeViewRepository.class);
        snapshotRepository = mock(MaintenanceSnapshotViewRepository.class);
        handler = new MaintenanceFieldConflictProjectionEventHandler(fieldRepository, snapshotRepository);
        fieldView = new MaintenanceFieldChangeView();
        fieldView.setItemCode("POLICY_INFO_CHANGE");
        fieldView.setObjectId("policy-1");
        fieldView.setFieldCode("policy.holder.mobile");
        snapshotView = new MaintenanceSnapshotView();
        when(fieldRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                "tenant-1", "case-1")).thenReturn(List.of(fieldView));
        when(snapshotRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(snapshotView));
    }

    @Test
    void shouldProjectDetectionAndResolutionAudit() {
        MaintenanceFieldChange conflict = conflict();
        MaintenanceFieldConflictPlan conflictPlan = plan(conflict, 1, "a".repeat(64));
        handler.on(new MaintenanceFieldConflictsRefreshedEvent(
                ID, "refresh-1", "b".repeat(64), conflictPlan, NOW, "operator-1", "tenant-1"));

        assertEquals(MaintenanceFieldConflictStatus.DETECTED, fieldView.getConflictStatus());
        assertEquals("refresh-1", fieldView.getConflictOperationId());
        assertEquals(8L, fieldView.getConflictPolicyVersion());
        assertEquals("13700000000", fieldView.getCurrentValue());
        assertNull(fieldView.getResolutionOperationId());

        MaintenanceFieldChange resolved = conflict.resolveUsingCurrent();
        MaintenanceFieldConflictPlan resolvedPlan = plan(resolved, 0, "c".repeat(64));
        handler.on(new MaintenanceFieldConflictResolvedEvent(
                ID, "resolve-1", "d".repeat(64), conflict, resolved,
                MaintenanceFieldConflictResolutionAction.USE_CURRENT, "采用 Policy 当前值",
                resolvedPlan, NOW.plusMinutes(1), "operator-2", "tenant-1"));

        assertEquals(MaintenanceFieldConflictStatus.RESOLVED, fieldView.getConflictStatus());
        assertEquals("13700000000", fieldView.getProposedValue());
        assertEquals("resolve-1", fieldView.getResolutionOperationId());
        assertEquals("采用 Policy 当前值", fieldView.getResolutionReason());
        assertEquals("operator-2", fieldView.getResolvedBy());
        assertEquals("c".repeat(64), snapshotView.getProposedContentHash());
        verify(fieldRepository, org.mockito.Mockito.times(2)).saveAll(List.of(fieldView));
    }

    private MaintenanceFieldChange conflict() {
        return MaintenanceFieldChange.propose(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"))
                .refreshCurrent(MaintenanceFieldValue.text("13700000000"));
    }

    private MaintenanceFieldConflictPlan plan(
            MaintenanceFieldChange change,
            int conflictCount,
            String contentHash) {
        MaintenanceSnapshotReference reference = new MaintenanceSnapshotReference(
                "maintenance://case-1/proposed", contentHash, 8, NOW);
        return new MaintenanceFieldConflictPlan(
                Map.of("POLICY_INFO_CHANGE", List.of(change)),
                Map.of("policy.holder.mobile", change.proposedValue()), reference, conflictCount);
    }
}
