package com.titanium.maintenance.query.handler.projection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.event.MaintenanceCaseInitializationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceCaseItemsPlannedEvent;
import com.titanium.maintenance.event.MaintenanceCaseOpenedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleAttemptedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleCompletedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleFailedEvent;
import com.titanium.maintenance.event.MaintenanceEffectSchedulePausedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleResumedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduledEvent;
import com.titanium.maintenance.event.MaintenanceEffectStatusChangedEvent;
import com.titanium.maintenance.event.MaintenanceFieldChangesRecordedEvent;
import com.titanium.maintenance.event.MaintenanceItemAddedEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalCompensationRecordedEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalFailedEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalRecoveryConfiguredEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalStartedEvent;
import com.titanium.maintenance.event.MaintenancePolicySnapshotCapturedEvent;
import com.titanium.maintenance.event.MaintenanceProposedSnapshotRecordedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowTaskTransitionedEvent;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldDescriptorSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.maintenance.valueobject.item.MaintenanceItemSelectionEvidence;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawal;

import lombok.RequiredArgsConstructor;

/** M3-06 独立案件、项目、字段差异和快照引用投影。 */
@Component
@ProcessingGroup("maintenance-query-group")
@RequiredArgsConstructor
public class MaintenanceCaseProjectionEventHandler {

    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceCaseItemViewRepository itemViewRepository;
    private final MaintenanceFieldChangeViewRepository fieldChangeViewRepository;
    private final MaintenanceSnapshotViewRepository snapshotViewRepository;

    @EventHandler
    @Transactional
    public void on(MaintenanceCaseOpenedEvent event) {
        MaintenanceView view = requireCase(event.maintenanceId().id(), event.tenantId());
        view.setSource(event.source());
        view.setClientRequestKey(event.clientRequestKey());
        view.setRequestFingerprint(event.requestFingerprint());
        view.setIndependentCase(true);
        view.setInitializationCompleted(false);
        view.setEffectStatus(MaintenanceEffectStatus.NOT_STARTED);
        view.setUpdatedBy(event.openedBy());
        view.setUpdateTime(event.openedAt());
        maintenanceViewRepository.save(view);
    }

    /** 投影案件正交生效状态，避免由任务状态临时推断。 */
    @EventHandler
    @Transactional
    public void on(MaintenanceEffectStatusChangedEvent event) {
        MaintenanceView view = requireCase(event.maintenanceId().id(), event.tenantId());
        view.setEffectStatus(event.currentStatus());
        if (event.currentStatus() == MaintenanceEffectStatus.APPLIED) {
            view.setStatus(MaintenanceStatus.COMPLETED);
        }
        view.setUpdatedBy(event.changedBy());
        view.setUpdateTime(event.changedAt());
        maintenanceViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceEffectScheduledEvent event) {
        MaintenanceView view = requireCase(event.maintenanceId().id(), event.tenantId());
        var schedule = event.schedule();
        view.setEffectScheduleId(schedule.scheduleId());
        view.setEffectScheduleStatus(schedule.status());
        view.setEffectScheduleTenantZoneId(schedule.tenantZoneId());
        view.setEffectScheduleNextExecutionAt(schedule.nextExecutionAt());
        view.setEffectScheduleAttemptCount(schedule.attemptCount());
        view.setEffectScheduleCreatedAt(schedule.createdAt());
        view.setEffectScheduleUpdatedAt(schedule.updatedAt());
        view.setUpdatedBy(event.scheduledBy());
        view.setUpdateTime(schedule.updatedAt());
        maintenanceViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceEffectSchedulePausedEvent event) {
        MaintenanceView view = requireSchedule(event.maintenanceId().id(), event.tenantId(), event.scheduleId());
        view.setEffectScheduleStatus(MaintenanceEffectScheduleStatus.PAUSED);
        view.setEffectScheduleUpdatedAt(event.pausedAt());
        view.setUpdatedBy(event.pausedBy());
        view.setUpdateTime(event.pausedAt());
        maintenanceViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceEffectScheduleResumedEvent event) {
        MaintenanceView view = requireSchedule(event.maintenanceId().id(), event.tenantId(), event.scheduleId());
        view.setEffectScheduleStatus(MaintenanceEffectScheduleStatus.ACTIVE);
        view.setEffectScheduleNextExecutionAt(event.nextExecutionAt());
        view.setEffectScheduleLastErrorCode(null);
        view.setEffectScheduleLastErrorMessage(null);
        view.setEffectScheduleUpdatedAt(event.resumedAt());
        view.setUpdatedBy(event.resumedBy());
        view.setUpdateTime(event.resumedAt());
        maintenanceViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceEffectScheduleAttemptedEvent event) {
        MaintenanceView view = requireSchedule(event.maintenanceId().id(), event.tenantId(), event.scheduleId());
        view.setEffectScheduleAttemptCount(event.attemptNumber());
        view.setEffectScheduleLastAttemptId(event.attemptId());
        view.setEffectScheduleLastAttemptAt(event.attemptedAt());
        view.setEffectScheduleLastErrorCode(null);
        view.setEffectScheduleLastErrorMessage(null);
        view.setEffectScheduleUpdatedAt(event.attemptedAt());
        view.setUpdatedBy(event.attemptedBy());
        view.setUpdateTime(event.attemptedAt());
        maintenanceViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceEffectScheduleFailedEvent event) {
        MaintenanceView view = requireSchedule(event.maintenanceId().id(), event.tenantId(), event.scheduleId());
        view.setEffectScheduleStatus(event.terminal()
                ? MaintenanceEffectScheduleStatus.FAILED : MaintenanceEffectScheduleStatus.ACTIVE);
        if (!event.terminal()) {
            view.setEffectScheduleNextExecutionAt(event.retryAt());
        }
        view.setEffectScheduleLastErrorCode(event.errorCode());
        view.setEffectScheduleLastErrorMessage(event.errorMessage());
        view.setEffectScheduleUpdatedAt(event.failedAt());
        view.setUpdatedBy(event.failedBy());
        view.setUpdateTime(event.failedAt());
        maintenanceViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceEffectScheduleCompletedEvent event) {
        MaintenanceView view = requireSchedule(event.maintenanceId().id(), event.tenantId(), event.scheduleId());
        view.setEffectScheduleStatus(MaintenanceEffectScheduleStatus.COMPLETED);
        view.setEffectScheduleLastErrorCode(null);
        view.setEffectScheduleLastErrorMessage(null);
        view.setEffectScheduleUpdatedAt(event.completedAt());
        view.setUpdatedBy(event.completedBy());
        view.setUpdateTime(event.completedAt());
        maintenanceViewRepository.save(view);
    }

    /** 报价任务补全案件重算检查点；Policy 成功回执补全字段实际值和 applied 快照。 */
    @EventHandler
    @Transactional
    public void on(MaintenanceWorkflowTaskTransitionedEvent event) {
        projectPremiumCalculationCheckpoint(event);
        var effectEvidence = event.afterTask().effectEvidence();
        if (effectEvidence == null || effectEvidence.application() == null) {
            return;
        }
        var application = effectEvidence.application();
        List<MaintenanceFieldChangeView> fieldViews = fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCodeOrderByFieldCodeAscObjectIdAsc(
                        event.tenantId(), event.maintenanceId().id(), event.afterTask().itemCode());
        application.appliedFields().forEach(applied -> fieldViews.stream()
                .filter(view -> view.getObjectId().equals(applied.objectId())
                        && view.getFieldCode().equals(applied.fieldCode()))
                .findFirst()
                .ifPresent(view -> {
                    view.setAppliedValue(applied.canonicalValue());
                    view.setUpdateTime(event.transitionedAt());
                }));
        fieldChangeViewRepository.saveAll(fieldViews);

        MaintenanceSnapshotView snapshotView = snapshotViewRepository
                .findByMaintenanceIdAndTenantId(event.maintenanceId().id(), event.tenantId())
                .orElseGet(MaintenanceSnapshotView::new);
        initializeSnapshotView(
                snapshotView, event.maintenanceId().id(), event.tenantId(), event.transitionedAt());
        applyApplied(snapshotView, application.appliedSnapshot());
        snapshotView.setUpdateTime(event.transitionedAt());
        snapshotViewRepository.save(snapshotView);
    }

    private void projectPremiumCalculationCheckpoint(MaintenanceWorkflowTaskTransitionedEvent event) {
        var quote = event.afterTask().premiumQuoteEvidence();
        if (quote == null || quote.status() != MaintenancePremiumQuoteStatus.QUOTED) {
            return;
        }
        MaintenanceView view = requireCase(event.maintenanceId().id(), event.tenantId());
        boolean checkpointAbsent = view.getOriginalCalculationId() == null
                && view.getReplacementCalculationId() == null;
        if (checkpointAbsent) {
            view.setOriginalCalculationId(quote.originalCalculationId());
            view.setReplacementCalculationId(quote.replacementCalculationId());
        } else if (!Objects.equals(view.getOriginalCalculationId(), quote.originalCalculationId())
                || !Objects.equals(view.getReplacementCalculationId(), quote.replacementCalculationId())) {
            view.setPremiumCalculationCheckpointConflict(true);
        }
        view.setUpdatedBy(event.operatedBy());
        view.setUpdateTime(event.transitionedAt());
        maintenanceViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenancePolicySnapshotCapturedEvent event) {
        MaintenanceView view = requireCase(event.maintenanceId().id(), event.tenantId());
        var snapshot = event.snapshot();
        view.setPolicyNumber(snapshot.policyNumber());
        view.setProductId(snapshot.productId());
        view.setProductVersion(snapshot.productVersion());
        view.setPlanVersion(snapshot.planVersion());
        view.setPolicyBaselineVersion(snapshot.policyVersion());
        view.setBusinessEffectiveAt(snapshot.businessEffectiveAt().toString());
        view.setUpdatedBy(event.recordedBy());
        view.setUpdateTime(event.recordedAt());
        maintenanceViewRepository.save(view);

        MaintenanceSnapshotView snapshotView = snapshotViewRepository
                .findByMaintenanceIdAndTenantId(event.maintenanceId().id(), event.tenantId())
                .orElseGet(MaintenanceSnapshotView::new);
        initializeSnapshotView(snapshotView, event.maintenanceId().id(), event.tenantId(), event.recordedAt());
        applyBefore(snapshotView, snapshot.beforeSnapshot());
        snapshotView.setUpdateTime(event.recordedAt());
        snapshotViewRepository.save(snapshotView);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceCaseItemsPlannedEvent event) {
        MaintenanceView view = requireCase(event.maintenanceId().id(), event.tenantId());
        view.setPlannedItemCount(event.itemCodes().size());
        view.setUpdatedBy(event.plannedBy());
        view.setUpdateTime(event.plannedAt());
        maintenanceViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceItemAddedEvent event) {
        MaintenanceItemInstance item = event.item();
        MaintenanceCaseItemView view = itemViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCode(
                        event.tenantId(), event.maintenanceId().id(), item.itemCode())
                .orElseGet(MaintenanceCaseItemView::new);
        if (view.getCreateTime() == null) {
            view.setItemViewId(itemViewId(event.tenantId(), event.maintenanceId().id(), item.itemCode()));
            view.setMaintenanceId(event.maintenanceId().id());
            view.setItemCode(item.itemCode());
            view.setTenantId(event.tenantId());
            view.setCreateTime(event.addedAt());
        }
        applyItem(view, item);
        view.setUpdateTime(event.addedAt());
        itemViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceItemWithdrawalStartedEvent event) {
        MaintenanceCaseItemView view = requireItem(
                event.tenantId(), event.maintenanceId().id(), event.withdrawal().itemCode());
        applyWithdrawal(view, event.withdrawal(), event.withdrawal().requestedBy());
        itemViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceItemWithdrawalRecoveryConfiguredEvent event) {
        MaintenanceCaseItemView view = requireItem(
                event.tenantId(), event.maintenanceId().id(), event.recoveryContext().itemCode());
        view.setWithdrawalPaymentMethod(event.recoveryContext().paymentMethod());
        view.setWithdrawalRecoveryConfiguredAt(event.recoveryContext().configuredAt());
        view.setWithdrawalUpdatedBy(event.recoveryContext().configuredBy());
        view.setUpdateTime(event.recoveryContext().configuredAt());
        itemViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceItemWithdrawalCompensationRecordedEvent event) {
        MaintenanceCaseItemView view = requireItem(
                event.tenantId(), event.maintenanceId().id(), event.withdrawal().itemCode());
        applyWithdrawal(view, event.withdrawal(), event.operatedBy());
        itemViewRepository.save(view);
        if (event.withdrawal().status() != MaintenanceItemWithdrawalStatus.COMPLETED) {
            return;
        }
        fieldChangeViewRepository.deleteByTenantIdAndMaintenanceIdAndItemCode(
                event.tenantId(), event.maintenanceId().id(), event.withdrawal().itemCode());
        if (event.proposedPlan() != null) {
            MaintenanceSnapshotView snapshotView = snapshotViewRepository
                    .findByMaintenanceIdAndTenantId(event.maintenanceId().id(), event.tenantId())
                    .orElseGet(MaintenanceSnapshotView::new);
            initializeSnapshotView(
                    snapshotView, event.maintenanceId().id(), event.tenantId(), event.withdrawal().completedAt());
            applyProposed(snapshotView, event.proposedPlan().proposedSnapshot());
            snapshotView.setUpdateTime(event.withdrawal().completedAt());
            snapshotViewRepository.save(snapshotView);
        }
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceItemWithdrawalFailedEvent event) {
        MaintenanceCaseItemView view = requireItem(
                event.tenantId(), event.maintenanceId().id(), event.withdrawal().itemCode());
        applyWithdrawal(view, event.withdrawal(), event.operatedBy());
        itemViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceCaseInitializationCompletedEvent event) {
        MaintenanceView view = requireCase(event.maintenanceId().id(), event.tenantId());
        view.setInitializationCompleted(true);
        view.setInitializationCompletedAt(event.completedAt());
        view.setUpdatedBy(event.completedBy());
        view.setUpdateTime(event.completedAt());
        maintenanceViewRepository.save(view);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceFieldChangesRecordedEvent event) {
        fieldChangeViewRepository.deleteByTenantIdAndMaintenanceIdAndItemCode(
                event.tenantId(), event.maintenanceId().id(), event.itemCode());
        List<MaintenanceFieldChangeView> views = event.changes().stream().map(change -> {
            MaintenanceFieldChangeView view = new MaintenanceFieldChangeView();
            view.setFieldChangeId(fieldChangeId(
                    event.tenantId(), event.maintenanceId().id(), event.itemCode(),
                    change.objectId(), change.fieldCode()));
            view.setMaintenanceId(event.maintenanceId().id());
            view.setItemCode(event.itemCode());
            view.setObjectId(change.objectId());
            view.setFieldCode(change.fieldCode());
            view.setDataType(change.baseValue().dataType());
            view.setBaseValue(change.baseValue().canonicalValue());
            view.setCurrentValue(change.currentValue().canonicalValue());
            view.setProposedValue(change.proposedValue().canonicalValue());
            view.setAppliedValue(change.appliedValue() == null
                    ? null
                    : change.appliedValue().canonicalValue());
            view.setConflictStatus(change.conflictStatus());
            view.setResolutionCode(change.resolutionCode());
            view.setTenantId(event.tenantId());
            view.setCreateTime(event.recordedAt());
            view.setUpdateTime(event.recordedAt());
            return view;
        }).toList();
        fieldChangeViewRepository.saveAll(views);
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceProposedSnapshotRecordedEvent event) {
        List<MaintenanceFieldChangeView> fieldViews = fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCodeOrderByFieldCodeAscObjectIdAsc(
                        event.tenantId(), event.maintenanceId().id(), event.itemCode());
        fieldViews.forEach(view -> applyDescriptor(
                view, event.fieldCatalogSnapshot().requireField(view.getFieldCode()), event.recordedAt().toLocalDateTime()));
        fieldChangeViewRepository.saveAll(fieldViews);

        MaintenanceSnapshotView snapshotView = snapshotViewRepository
                .findByMaintenanceIdAndTenantId(event.maintenanceId().id(), event.tenantId())
                .orElseGet(MaintenanceSnapshotView::new);
        initializeSnapshotView(
                snapshotView, event.maintenanceId().id(), event.tenantId(), event.recordedAt().toLocalDateTime());
        applyProposed(snapshotView, event.proposedSnapshot());
        snapshotView.setUpdateTime(event.recordedAt().toLocalDateTime());
        snapshotViewRepository.save(snapshotView);
    }

    private MaintenanceView requireCase(String maintenanceId, String tenantId) {
        return maintenanceViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .orElseThrow(() -> new IllegalStateException("独立保全案件主投影尚未建立: " + maintenanceId));
    }

    private MaintenanceCaseItemView requireItem(String tenantId, String maintenanceId, String itemCode) {
        return itemViewRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                tenantId, maintenanceId, itemCode)
                .orElseThrow(() -> new IllegalStateException("保全项目投影尚未建立: " + itemCode));
    }

    private MaintenanceView requireSchedule(String maintenanceId, String tenantId, String scheduleId) {
        MaintenanceView view = requireCase(maintenanceId, tenantId);
        if (!scheduleId.equals(view.getEffectScheduleId())) {
            throw new IllegalStateException("未来生效计划投影不存在或不匹配: " + scheduleId);
        }
        return view;
    }

    private void applyItem(MaintenanceCaseItemView view, MaintenanceItemInstance item) {
        view.setItemName(item.name());
        view.setItemCategory(item.category().name());
        view.setConfigurationVersion(item.configVersion());
        view.setSelectedAt(item.selectedAt());
        MaintenanceItemSelectionEvidence evidence = item.selectionEvidence();
        view.setConfigurationId(evidence.configurationId());
        view.setConfigurationContentHash(evidence.configurationContentHash());
        view.setOfferingId(evidence.offeringId());
        view.setOfferingVersion(evidence.offeringVersion());
        view.setOfferingContentHash(evidence.offeringContentHash());
        view.setEvidenceResolvedAt(evidence.resolvedAt() == null ? null : evidence.resolvedAt().toString());
    }

    private void applyWithdrawal(
            MaintenanceCaseItemView view,
            MaintenanceItemWithdrawal withdrawal,
            String operatedBy) {
        var sourcePosting = withdrawal.sourcePosting();
        var compensation = withdrawal.compensation();
        var reversal = compensation == null ? null : compensation.reversal();
        view.setWithdrawalStatus(withdrawal.status());
        view.setWithdrawalOperationId(withdrawal.operationId());
        view.setWithdrawalRequestHash(withdrawal.requestHash());
        view.setWithdrawalReason(withdrawal.reason());
        view.setWithdrawalSourcePostingId(sourcePosting == null ? null : sourcePosting.postingId());
        view.setWithdrawalSourceResultHash(sourcePosting == null ? null : sourcePosting.resultHash());
        view.setWithdrawalSourceDirection(sourcePosting == null ? null : sourcePosting.direction());
        view.setWithdrawalSourceFundStatus(compensation == null ? null : compensation.sourceFundStatus());
        view.setWithdrawalReversalId(reversal == null ? null : reversal.reversalId());
        view.setWithdrawalReversalResultHash(reversal == null ? null : reversal.resultHash());
        view.setWithdrawalReversalDirection(reversal == null ? null : reversal.direction());
        view.setWithdrawalAmount(compensation == null ? null : compensation.amount());
        view.setWithdrawalCurrency(compensation == null ? null : compensation.currency());
        view.setWithdrawalFundAction(compensation == null ? null : compensation.fundAction());
        view.setWithdrawalFundStatus(compensation == null ? null : compensation.fundStatus());
        view.setWithdrawalFundRequestId(compensation == null ? null : compensation.fundRequestId());
        view.setWithdrawalFundOrderId(compensation == null ? null : compensation.fundOrderId());
        view.setWithdrawalFundExternalStatus(compensation == null ? null : compensation.fundExternalStatus());
        view.setWithdrawalFailureCode(withdrawal.failureCode());
        view.setWithdrawalFailureMessage(withdrawal.failureMessage());
        view.setWithdrawalRetryCount(withdrawal.retryCount());
        view.setWithdrawalRequestedAt(withdrawal.requestedAt());
        view.setWithdrawalCompletedAt(withdrawal.completedAt());
        view.setWithdrawalRequestedBy(withdrawal.requestedBy());
        view.setWithdrawalUpdatedBy(operatedBy);
        view.setUpdateTime(withdrawal.updatedAt());
    }

    private void applyDescriptor(
            MaintenanceFieldChangeView view,
            MaintenanceFieldDescriptorSnapshot descriptor,
            LocalDateTime updatedAt) {
        view.setLabelKey(descriptor.labelKey());
        view.setSensitivity(descriptor.sensitivity());
        view.setMaskingPolicy(descriptor.maskingPolicy());
        view.setChangeTypeCode(descriptor.changeTypeCode());
        view.setUpdateTime(updatedAt);
    }

    private void initializeSnapshotView(
            MaintenanceSnapshotView view, String maintenanceId, String tenantId, LocalDateTime createdAt) {
        if (view.getCreateTime() == null) {
            view.setMaintenanceId(maintenanceId);
            view.setTenantId(tenantId);
            view.setCreateTime(createdAt);
        }
    }

    private void applyBefore(MaintenanceSnapshotView view, MaintenanceSnapshotReference reference) {
        view.setBeforeStorageKey(reference.storageKey());
        view.setBeforeContentHash(reference.contentHash());
        view.setBeforePolicyVersion(reference.policyVersion());
        view.setBeforeCapturedAt(reference.capturedAt().toString());
    }

    private void applyProposed(MaintenanceSnapshotView view, MaintenanceSnapshotReference reference) {
        view.setProposedStorageKey(reference.storageKey());
        view.setProposedContentHash(reference.contentHash());
        view.setProposedPolicyVersion(reference.policyVersion());
        view.setProposedCapturedAt(reference.capturedAt().toString());
    }

    private void applyApplied(MaintenanceSnapshotView view, MaintenanceSnapshotReference reference) {
        view.setAppliedStorageKey(reference.storageKey());
        view.setAppliedContentHash(reference.contentHash());
        view.setAppliedPolicyVersion(reference.policyVersion());
        view.setAppliedCapturedAt(reference.capturedAt().toString());
    }

    private String itemViewId(String tenantId, String maintenanceId, String itemCode) {
        return tenantId + ":" + maintenanceId + ":" + itemCode;
    }

    private String fieldChangeId(
            String tenantId, String maintenanceId, String itemCode, String objectId, String fieldCode) {
        String canonical = String.join("\u001f", tenantId, maintenanceId, itemCode, objectId, fieldCode);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }
}
