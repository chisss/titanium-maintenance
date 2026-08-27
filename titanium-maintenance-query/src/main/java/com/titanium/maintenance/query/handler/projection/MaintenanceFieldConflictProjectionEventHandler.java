package com.titanium.maintenance.query.handler.projection;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.event.MaintenanceFieldConflictResolvedEvent;
import com.titanium.maintenance.event.MaintenanceFieldConflictsRefreshedEvent;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;

import lombok.RequiredArgsConstructor;

/** 顺序外字段冲突及解决证据投影。 */
@Component
@ProcessingGroup("maintenance-query-group")
@RequiredArgsConstructor
public class MaintenanceFieldConflictProjectionEventHandler {

    private final MaintenanceFieldChangeViewRepository fieldChangeViewRepository;
    private final MaintenanceSnapshotViewRepository snapshotViewRepository;

    @EventHandler
    @Transactional
    public void on(MaintenanceFieldConflictsRefreshedEvent event) {
        LocalDateTime refreshedAt = event.refreshedAt().toLocalDateTime();
        List<MaintenanceFieldChangeView> views = requireFieldViews(event.tenantId(), event.maintenanceId().id());
        event.plan().allChanges().forEach(change -> {
            MaintenanceFieldChangeView view = requireFieldView(views, change);
            applyChange(view, change, refreshedAt);
            if (change.conflictStatus() == MaintenanceFieldConflictStatus.DETECTED) {
                view.setConflictOperationId(event.operationId());
                view.setConflictDetectedAt(refreshedAt);
                view.setConflictPolicyVersion(event.plan().proposedSnapshot().policyVersion());
                view.setConflictEvidenceHash(event.operationHash());
                clearResolution(view);
            }
        });
        fieldChangeViewRepository.saveAll(views);
        applyProposedSnapshot(event.tenantId(), event.maintenanceId().id(), event.plan(), refreshedAt);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceFieldConflictResolvedEvent event) {
        LocalDateTime resolvedAt = event.resolvedAt().toLocalDateTime();
        List<MaintenanceFieldChangeView> views = requireFieldViews(event.tenantId(), event.maintenanceId().id());
        event.plan().allChanges().forEach(change -> applyChange(requireFieldView(views, change), change, resolvedAt));
        MaintenanceFieldChangeView resolved = requireFieldView(views, event.afterChange());
        resolved.setResolutionOperationId(event.operationId());
        resolved.setResolutionReason(event.reason());
        resolved.setResolutionEvidenceHash(event.operationHash());
        resolved.setResolvedBy(event.resolvedBy());
        resolved.setResolvedAt(resolvedAt);
        fieldChangeViewRepository.saveAll(views);
        applyProposedSnapshot(event.tenantId(), event.maintenanceId().id(), event.plan(), resolvedAt);
    }

    private List<MaintenanceFieldChangeView> requireFieldViews(String tenantId, String maintenanceId) {
        List<MaintenanceFieldChangeView> views = fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(tenantId, maintenanceId);
        if (views.isEmpty()) {
            throw new IllegalStateException("字段冲突事件对应的字段投影不存在: " + maintenanceId);
        }
        return views;
    }

    private MaintenanceFieldChangeView requireFieldView(
            List<MaintenanceFieldChangeView> views,
            MaintenanceFieldChange change) {
        return views.stream()
                .filter(view -> view.getItemCode().equals(change.itemCode())
                        && view.getObjectId().equals(change.objectId())
                        && view.getFieldCode().equals(change.fieldCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "字段冲突事件引用了不存在的字段投影: " + change.key()));
    }

    private void applyChange(
            MaintenanceFieldChangeView view,
            MaintenanceFieldChange change,
            LocalDateTime updatedAt) {
        view.setCurrentValue(change.currentValue().canonicalValue());
        view.setProposedValue(change.proposedValue().canonicalValue());
        view.setAppliedValue(change.appliedValue() == null ? null : change.appliedValue().canonicalValue());
        view.setConflictStatus(change.conflictStatus());
        view.setResolutionCode(change.resolutionCode());
        view.setUpdateTime(updatedAt);
    }

    private void clearResolution(MaintenanceFieldChangeView view) {
        view.setResolutionOperationId(null);
        view.setResolutionReason(null);
        view.setResolutionEvidenceHash(null);
        view.setResolvedBy(null);
        view.setResolvedAt(null);
    }

    private void applyProposedSnapshot(
            String tenantId,
            String maintenanceId,
            MaintenanceFieldConflictPlan plan,
            LocalDateTime updatedAt) {
        MaintenanceSnapshotView view = snapshotViewRepository
                .findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "字段冲突事件对应的快照投影不存在: " + maintenanceId));
        MaintenanceSnapshotReference reference = plan.proposedSnapshot();
        view.setProposedStorageKey(reference.storageKey());
        view.setProposedContentHash(reference.contentHash());
        view.setProposedPolicyVersion(reference.policyVersion());
        view.setProposedCapturedAt(reference.capturedAt().toString());
        view.setUpdateTime(updatedAt);
        snapshotViewRepository.save(view);
    }
}
