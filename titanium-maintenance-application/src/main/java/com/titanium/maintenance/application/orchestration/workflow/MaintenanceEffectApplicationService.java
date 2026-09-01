package com.titanium.maintenance.application.orchestration.workflow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.effect.MaintenanceEffectApplicationInput;
import com.titanium.maintenance.application.command.field.RefreshMaintenanceFieldConflictsInput;
import com.titanium.maintenance.application.model.effect.MaintenanceEffectApplicationResult;
import com.titanium.maintenance.application.model.field.MaintenanceFieldConflictOperationResult;
import com.titanium.maintenance.command.FailMaintenanceCaseEffectCommand;
import com.titanium.maintenance.command.RecordMaintenanceCasePolicyApplicationCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectCompensationCommand;
import com.titanium.maintenance.command.RequestMaintenanceCaseEffectCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort.ResolutionFact;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogRequest;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.ApplicationFact;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.ApplicationRequest;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.FieldChange;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.RetroactiveEvidence;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.item.MaintenanceItemCode;
import com.titanium.maintenance.valueobject.workflow.MaintenanceAppliedFieldEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectCompensationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectSchedule;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyApplicationEvidence;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.TerminationReason;

import lombok.RequiredArgsConstructor;

/** 编排“冻结请求事实 → Policy 正式应用 → 权威回执事实”的正式生效用例。 */
@Service
@RequiredArgsConstructor
public class MaintenanceEffectApplicationService {

    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceWorkflowTaskViewRepository taskViewRepository;
    private final MaintenanceFieldChangeViewRepository fieldChangeViewRepository;
    private final MaintenanceSnapshotViewRepository snapshotViewRepository;
    private final MaintenanceRetroactivePeriodAdjustmentViewRepository periodAdjustmentViewRepository;
    private final MaintenanceFieldConflictApplicationService fieldConflictApplicationService;
    private final PolicyFieldCatalogPort policyFieldCatalogPort;
    private final PolicyMaintenanceApplicationPort policyApplicationPort;
    private final BillingRetroactivePeriodResolutionPort billingResolutionPort;

    public CompletableFuture<MaintenanceEffectApplicationResult> apply(MaintenanceEffectApplicationInput input) {
        EffectContext context = requireContext(input, false, null);
        MaintenanceEffectApplicationResult recovered = recoverCompletedResult(context.effectTasks());
        if (recovered != null) {
            return CompletableFuture.completedFuture(recovered);
        }
        EffectiveTimeType timeType = context.caseView().getEffectiveTimeType();
        LocalDateTime effectiveAt = timeType == EffectiveTimeType.RETROACTIVE
                ? context.caseView().getSpecificEffectiveDate() : LocalDateTime.now();
        return prepareConflicts(input, context).thenCompose(prepared -> {
            ApplicationRequest request = buildRequest(input, prepared, timeType, effectiveAt);
            CompletableFuture<Void> requestFrozen = freezeRequestIfRequired(input, prepared, request);
            return requestFrozen.thenCompose(ignored -> invokePolicy(input, prepared, request));
        });
    }

    /** 调度租约持有者专用入口；计划创建阶段不会调用此方法。 */
    public CompletableFuture<MaintenanceEffectApplicationResult> applyScheduled(
            MaintenanceEffectApplicationInput input,
            LocalDateTime scheduledEffectiveAt) {
        EffectContext context = requireContext(input, true, scheduledEffectiveAt);
        MaintenanceEffectApplicationResult recovered = recoverCompletedResult(context.effectTasks());
        if (recovered != null) {
            return CompletableFuture.completedFuture(recovered);
        }
        return prepareConflicts(input, context).thenCompose(prepared -> {
            ApplicationRequest request = buildRequest(
                    input, prepared, prepared.caseView().getEffectiveTimeType(), scheduledEffectiveAt);
            CompletableFuture<Void> requestFrozen = freezeRequestIfRequired(input, prepared, request);
            return requestFrozen.thenCompose(ignored -> invokePolicy(input, prepared, request));
        });
    }

    private CompletableFuture<EffectContext> prepareConflicts(
            MaintenanceEffectApplicationInput input,
            EffectContext context) {
        if (context.effectTasks().getFirst().getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL) {
            return CompletableFuture.completedFuture(context);
        }
        long expectedVersion = context.expectedPolicyVersion();
        RefreshMaintenanceFieldConflictsInput refreshInput = new RefreshMaintenanceFieldConflictsInput(
                input.maintenanceId(), input.operationId() + ":conflict-refresh",
                input.operatorId(), input.tenantId());
        return fieldConflictApplicationService.refreshIfVersionChanged(refreshInput, expectedVersion)
                .thenApply(result -> {
                    if (result != null && result.conflictCount() > 0) {
                        throw validation("fieldChanges", "Policy 已发生顺序外变更，必须先显式解决字段冲突");
                    }
                    return context.withRefreshedConflicts(result);
                });
    }

    private CompletableFuture<MaintenanceEffectApplicationResult> invokePolicy(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            ApplicationRequest request) {
        ApplicationFact fact;
        try {
            fact = policyApplicationPort.apply(request);
        } catch (RuntimeException exception) {
            return recordFailure(input, context, exception);
        }
        MaintenancePolicyApplicationEvidence evidence;
        try {
            evidence = toEvidence(request, fact);
        } catch (RuntimeException exception) {
            return recordCompensation(input, context, fact, exception);
        }
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "receipt", context.retryCount());
        return send(new RecordMaintenanceCasePolicyApplicationCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId()))
                .thenApply(ignored -> new MaintenanceEffectApplicationResult(
                        fact.requestId(), fact.endorsementNo(), fact.actualPolicyVersion(),
                        fact.applicationHash(), fact.appliedAt()))
                .exceptionallyCompose(exception -> recordCompensation(
                        input, context, fact, rootCause(exception)));
    }

    private CompletableFuture<MaintenanceEffectApplicationResult> recordCompensation(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            ApplicationFact fact,
            Throwable exception) {
        RuntimeException cause = exception instanceof RuntimeException runtimeException
                ? runtimeException : new IllegalStateException(exception);
        String compensationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "compensation", context.retryCount());
        MaintenanceEffectCompensationEvidence evidence = new MaintenanceEffectCompensationEvidence(
                compensationId, fact.requestId(), fact.endorsementNo(), fact.actualPolicyVersion(),
                fact.applicationHash(), failureReason(cause), LocalDateTime.now(), input.operatorId());
        return send(new RecordMaintenanceEffectCompensationCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds().getFirst(), evidence,
                input.operatorId()))
                .thenCompose(ignored -> CompletableFuture.failedFuture(cause));
    }

    private CompletableFuture<MaintenanceEffectApplicationResult> recordFailure(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            RuntimeException exception) {
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "failure", context.retryCount());
        String reason = failureReason(exception);
        return send(new FailMaintenanceCaseEffectCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                "POLICY_APPLICATION_FAILED", reason, input.operatorId()))
                .thenCompose(ignored -> CompletableFuture.failedFuture(exception));
    }

    private CompletableFuture<Void> freezeRequestIfRequired(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            ApplicationRequest request) {
        if (context.effectTasks().getFirst().getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL) {
            context.effectTasks().forEach(task -> validateFrozenRequest(task, request));
            return CompletableFuture.completedFuture(null);
        }
        LocalDateTime requestedAt = LocalDateTime.now();
        MaintenanceEffectRequestEvidence evidence = new MaintenanceEffectRequestEvidence(
                request.requestId(), request.requestPayloadHash(), request.expectedPolicyVersion(),
                EffectiveTimeType.fromCode(request.effectiveTimeType()), request.effectiveAt(),
                request.proposedSnapshotHash(), requestedAt);
        String operationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "request", context.retryCount());
        return send(new RequestMaintenanceCaseEffectCommand(
                MaintenanceId.of(input.maintenanceId()), context.taskIds(), operationId,
                evidence, input.operatorId()));
    }

    private ApplicationRequest buildRequest(
            MaintenanceEffectApplicationInput input,
            EffectContext context,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime requestedEffectiveAt) {
        MaintenanceWorkflowTaskView evidenceTask = context.effectTasks().getFirst();
        boolean reuseFrozenRequest = evidenceTask.getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL;
        LocalDateTime effectiveAt = reuseFrozenRequest && evidenceTask.getEffectRequestedEffectiveAt() != null
                ? evidenceTask.getEffectRequestedEffectiveAt() : requestedEffectiveAt;
        EffectiveTimeType requestTimeType = reuseFrozenRequest && evidenceTask.getEffectTimeType() != null
                ? evidenceTask.getEffectTimeType() : effectiveTimeType;
        long expectedVersion = reuseFrozenRequest && evidenceTask.getEffectExpectedPolicyVersion() != null
                ? evidenceTask.getEffectExpectedPolicyVersion()
                : context.expectedPolicyVersion();
        List<FieldChange> changes = context.applicationFieldChanges();
        PolicyMaintenanceAction stateAction = stateAction(context.effectTasks());
        if (changes.isEmpty() && !stateAction.changesStatus()) {
            throw validation("fieldChanges", "字段冲突解决后案件已无实际变更，不能提交空 Policy 交易");
        }
        String summary = changeSummary(input.maintenanceId(), context.effectTasks(), changes, stateAction);
        String requestId = reuseFrozenRequest && evidenceTask.getEffectRequestId() != null
                ? evidenceTask.getEffectRequestId()
                : PolicyMaintenanceApplicationPort.stableCaseRequestId(
                        input.tenantId(), input.maintenanceId());
        return new ApplicationRequest(
                input.tenantId(), context.caseView().getPolicyId(),
                requestId,
                input.maintenanceId(), expectedVersion, null,
                context.proposedSnapshotHash(), requestTimeType.getCode(),
                effectiveAt, summary, changes, stateAction, stateReason(input.maintenanceId(), stateAction),
                terminationReason(context.effectTasks(), stateAction), context.retroactiveEvidence(),
                input.operatorId());
    }

    private EffectContext requireContext(
            MaintenanceEffectApplicationInput input,
            boolean scheduled,
            LocalDateTime scheduledEffectiveAt) {
        if (input == null || input.source() == null) {
            throw validation("input", "生效请求上下文不能为空");
        }
        MaintenanceView caseView = maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        MaintenanceWorkflowTaskView task = taskViewRepository
                .findByTenantIdAndMaintenanceIdAndTaskId(
                        input.tenantId(), input.maintenanceId(), input.taskId())
                .orElseThrow(MaintenanceNotFoundException::new);
        if (task.getStepType() != MaintenanceStepType.EFFECT
                || (task.getStatus() != MaintenanceWorkflowTaskStatus.READY
                        && task.getStatus() != MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL
                        && task.getStatus() != MaintenanceWorkflowTaskStatus.COMPLETED)) {
            throw validation("task", "生效任务必须处于 READY、WAITING_EXTERNAL 或 COMPLETED");
        }
        List<MaintenanceWorkflowTaskView> effectTasks = taskViewRepository
                .findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                        input.tenantId(), input.maintenanceId()).stream()
                .filter(candidate -> candidate.getStepType() == MaintenanceStepType.EFFECT)
                .filter(candidate -> candidate.getStatus() != MaintenanceWorkflowTaskStatus.SKIPPED)
                .toList();
        if (effectTasks.isEmpty() || effectTasks.stream().noneMatch(candidate ->
                Objects.equals(candidate.getTaskId(), input.taskId()))) {
            throw validation("effectTasks", "案件不存在请求指定的可执行生效任务");
        }
        boolean allReady = effectTasks.stream().allMatch(candidate ->
                candidate.getStatus() == MaintenanceWorkflowTaskStatus.READY);
        boolean allWaiting = effectTasks.stream().allMatch(candidate ->
                candidate.getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL);
        boolean allCompleted = effectTasks.stream().allMatch(candidate ->
                candidate.getStatus() == MaintenanceWorkflowTaskStatus.COMPLETED);
        if (!allReady && !allWaiting && !allCompleted) {
            throw validation("effectTasks", "案件全部生效任务必须同时处于 READY、WAITING_EXTERNAL 或 COMPLETED");
        }
        if (allCompleted) {
            return new EffectContext(caseView, effectTasks, null, List.of(), null, null);
        }
        RetroactiveEvidence retroactiveEvidence = null;
        if (!scheduled && caseView.getEffectiveTimeType() == EffectiveTimeType.RETROACTIVE) {
            retroactiveEvidence = requireRetroactiveEvidence(input, caseView);
        } else if (!scheduled && caseView.getEffectiveTimeType() != EffectiveTimeType.IMMEDIATE) {
            throw validation("effectiveTimeType", "未来生效案件必须由计划调度或人工立即执行入口处理");
        }
        if (scheduled && (!MaintenanceEffectSchedule.supportsScheduling(caseView.getEffectiveTimeType())
                || scheduledEffectiveAt == null)) {
            throw validation("effectiveTimeType", "当前案件不是可调度的未来生效案件");
        }
        MaintenanceSnapshotView snapshot = snapshotViewRepository
                .findByMaintenanceIdAndTenantId(input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        List<MaintenanceFieldChangeView> fields = fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                        input.tenantId(), input.maintenanceId());
        if (fields.stream().anyMatch(
                field -> field.getConflictStatus() == MaintenanceFieldConflictStatus.DETECTED)) {
            throw validation("fieldChanges", "生效不能存在未解决字段冲突");
        }
        PolicyMaintenanceAction stateAction = stateAction(effectTasks);
        if (fields.isEmpty() && !stateAction.changesStatus()) {
            throw validation("fieldChanges", "字段型案件必须包含结构化字段变更");
        }
        if (!fields.isEmpty()) {
            LocalDateTime capabilityEffectiveAt = scheduled
                    ? scheduledEffectiveAt
                    : caseView.getEffectiveTimeType() == EffectiveTimeType.RETROACTIVE
                            ? caseView.getSpecificEffectiveDate()
                            : effectiveAt(effectTasks.getFirst());
            validateExecutionCapabilities(input, fields, effectiveDate(capabilityEffectiveAt));
        }
        return new EffectContext(caseView, effectTasks, snapshot, fields, retroactiveEvidence, null);
    }

    private MaintenanceEffectApplicationResult recoverCompletedResult(
            List<MaintenanceWorkflowTaskView> effectTasks) {
        if (effectTasks.stream().anyMatch(task -> task.getStatus() != MaintenanceWorkflowTaskStatus.COMPLETED)) {
            return null;
        }
        MaintenanceWorkflowTaskView authoritative = effectTasks.getFirst();
        validateCompletedEvidence(authoritative);
        effectTasks.stream().skip(1).forEach(task -> {
            validateCompletedEvidence(task);
            if (!sameCompletedEvidence(authoritative, task)) {
                throw validation("policyApplication", "已完成生效任务未共享同一 Policy 请求和权威回执");
            }
        });
        return new MaintenanceEffectApplicationResult(
                authoritative.getEffectRequestId(), authoritative.getPolicyEndorsementNo(),
                authoritative.getPolicyActualVersion(), authoritative.getPolicyApplicationHash(),
                authoritative.getPolicyAppliedAt());
    }

    private void validateCompletedEvidence(MaintenanceWorkflowTaskView task) {
        boolean invalidRequest = !hasText(task.getEffectRequestId())
                || !isHash(task.getEffectRequestHash())
                || task.getEffectExpectedPolicyVersion() == null
                || task.getEffectTimeType() == null
                || task.getEffectRequestedEffectiveAt() == null
                || !isHash(task.getEffectProposedSnapshotHash());
        boolean invalidApplication = !hasText(task.getPolicyEndorsementNo())
                || task.getPolicyActualVersion() == null
                || !isHash(task.getPolicyApplicationHash())
                || task.getPolicyStateAction() == null
                || task.getPolicyAppliedAt() == null;
        boolean invalidSnapshot = !hasText(task.getAppliedSnapshotStorageKey())
                || !isHash(task.getAppliedSnapshotHash())
                || !Objects.equals(task.getPolicyActualVersion(), task.getAppliedSnapshotPolicyVersion())
                || !hasText(task.getAppliedSnapshotCapturedAt())
                || !hasText(task.getAppliedFieldsJson());
        boolean invalidStatusChange = task.getPolicyStateAction() != null
                && task.getPolicyStateAction().changesStatus()
                && (!hasText(task.getPolicyStatusBefore()) || !hasText(task.getPolicyStatusAfter()));
        if (invalidRequest || invalidApplication || invalidSnapshot || invalidStatusChange) {
            throw validation("policyApplication", "已完成生效任务缺少完整 Policy 请求、回执或应用快照投影");
        }
    }

    private boolean sameCompletedEvidence(
            MaintenanceWorkflowTaskView expected,
            MaintenanceWorkflowTaskView actual) {
        return Objects.equals(expected.getEffectRequestId(), actual.getEffectRequestId())
                && Objects.equals(expected.getEffectRequestHash(), actual.getEffectRequestHash())
                && Objects.equals(expected.getEffectExpectedPolicyVersion(), actual.getEffectExpectedPolicyVersion())
                && Objects.equals(expected.getEffectTimeType(), actual.getEffectTimeType())
                && Objects.equals(expected.getEffectRequestedEffectiveAt(), actual.getEffectRequestedEffectiveAt())
                && Objects.equals(expected.getEffectProposedSnapshotHash(), actual.getEffectProposedSnapshotHash())
                && Objects.equals(expected.getPolicyEndorsementNo(), actual.getPolicyEndorsementNo())
                && Objects.equals(expected.getPolicyActualVersion(), actual.getPolicyActualVersion())
                && Objects.equals(expected.getPolicyApplicationHash(), actual.getPolicyApplicationHash())
                && Objects.equals(expected.getPolicyStateAction(), actual.getPolicyStateAction())
                && Objects.equals(expected.getPolicyStatusBefore(), actual.getPolicyStatusBefore())
                && Objects.equals(expected.getPolicyStatusAfter(), actual.getPolicyStatusAfter())
                && Objects.equals(expected.getAppliedSnapshotStorageKey(), actual.getAppliedSnapshotStorageKey())
                && Objects.equals(expected.getAppliedSnapshotHash(), actual.getAppliedSnapshotHash())
                && Objects.equals(expected.getAppliedSnapshotPolicyVersion(), actual.getAppliedSnapshotPolicyVersion())
                && Objects.equals(expected.getAppliedSnapshotCapturedAt(), actual.getAppliedSnapshotCapturedAt())
                && Objects.equals(expected.getAppliedFieldsJson(), actual.getAppliedFieldsJson())
                && Objects.equals(expected.getPolicyAppliedAt(), actual.getPolicyAppliedAt());
    }

    private RetroactiveEvidence requireRetroactiveEvidence(
            MaintenanceEffectApplicationInput input,
            MaintenanceView view) {
        if (view.getSpecificEffectiveDate() == null
                || view.getRetroactiveImpactStatus() != MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED
                || view.getRetroactiveImpactBlockingCount() != 0
                || view.getRetroactiveImpactPendingCount() != 0) {
            throw validation("retroactiveImpact", "追溯影响分析未完成或仍存在阻断、待处理影响项");
        }
        if (view.getRetroactivePeriodRecalculationStatus()
                        != MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED
                && view.getRetroactivePeriodRecalculationStatus()
                        != MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED) {
            throw validation("retroactivePeriodRecalculation", "追溯期间重算尚未完成");
        }
        if (!hasText(view.getRetroactiveImpactAnalysisId())
                || value(view.getRetroactiveImpactAnalysisVersion()) < 1
                || !isHash(view.getRetroactiveImpactResultHash())
                || !hasText(view.getRetroactivePeriodRecalculationId())
                || value(view.getRetroactivePeriodRecalculationVersion()) < 1
                || !Objects.equals(view.getRetroactiveImpactAnalysisId(), view.getRetroactivePeriodAnalysisId())
                || !Objects.equals(view.getRetroactiveImpactAnalysisVersion(),
                        view.getRetroactivePeriodAnalysisVersion())
                || !Objects.equals(view.getRetroactiveImpactResultHash(),
                        view.getRetroactivePeriodAnalysisResultHash())
                || !hasText(view.getRetroactiveProductRecalculationId())
                || !hasText(view.getRetroactiveProductRecalculationVersion())
                || !isHash(view.getRetroactiveProductInputHash())
                || !isHash(view.getRetroactiveProductResultHash())
                || !hasText(view.getRetroactiveBillingBatchId())
                || !isHash(view.getRetroactiveBillingResultHash())) {
            throw validation("retroactiveEvidence", "追溯分析、Product或Billing检查点不完整或已经漂移");
        }
        validateRecalculationPeriods(input, view);

        ResolutionFact resolution = null;
        if (view.getRetroactivePeriodRecalculationStatus()
                == MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED) {
            resolution = requireBillingResolution(input, view);
        } else if (view.getRetroactiveBillingReviewCount() != 0
                || view.getRetroactivePeriodResolutionStatus() != null) {
            throw validation("retroactiveResolution", "无关闭期间的重算结果携带了非法处理结论");
        }
        return new RetroactiveEvidence(
                view.getRetroactiveImpactAnalysisId(), value(view.getRetroactiveImpactAnalysisVersion()),
                view.getRetroactiveImpactResultHash(), view.getRetroactivePeriodRecalculationId(),
                value(view.getRetroactivePeriodRecalculationVersion()),
                view.getRetroactiveProductRecalculationId(), view.getRetroactiveProductRecalculationVersion(),
                view.getRetroactiveProductInputHash(), view.getRetroactiveProductResultHash(),
                view.getRetroactiveBillingBatchId(), view.getRetroactiveBillingResultHash(),
                view.getRetroactiveBillingStatus(),
                resolution == null ? null : resolution.billingResolutionId(),
                resolution == null ? null : resolution.resultHash(),
                resolution == null ? null : resolution.targetAccountingPeriod().toString(),
                resolution == null ? 0 : resolution.resolvedLineCount());
    }

    private void validateRecalculationPeriods(
            MaintenanceEffectApplicationInput input,
            MaintenanceView view) {
        List<MaintenanceRetroactivePeriodAdjustmentView> periods = periodAdjustmentViewRepository
                .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                        input.tenantId(), input.maintenanceId(), view.getRetroactivePeriodRecalculationId());
        if (periods.size() != view.getRetroactivePeriodCount()
                || periods.stream().anyMatch(period -> !isHash(period.getProductResultHash())
                        || !isHash(period.getBillingResultHash())
                        || !hasText(period.getBillingStatus()))) {
            throw validation("retroactiveEvidence", "追溯逐期间检查点数量或摘要不完整");
        }
    }

    private ResolutionFact requireBillingResolution(
            MaintenanceEffectApplicationInput input,
            MaintenanceView view) {
        if (view.getRetroactivePeriodResolutionStatus()
                        != MaintenanceRetroactivePeriodResolutionStatus.COMPLETED
                || !hasText(view.getRetroactiveBillingResolutionId())
                || !Objects.equals(view.getRetroactiveBillingResultHash(),
                        view.getRetroactivePeriodResolutionSourceBatchHash())
                || !isHash(view.getRetroactivePeriodResolutionResultHash())
                || view.getRetroactivePeriodResolutionResolvedLineCount()
                        != view.getRetroactiveBillingReviewCount()) {
            throw validation("retroactiveResolution", "关闭会计期间处理结论尚未完成或与当前批次不一致");
        }
        ResolutionFact fact = billingResolutionPort.get(
                input.tenantId(), view.getRetroactiveBillingBatchId());
        if (fact == null || !Objects.equals("COMPLETED", fact.status())
                || !Objects.equals(input.tenantId(), fact.tenantId())
                || !Objects.equals(input.maintenanceId(), fact.maintenanceId())
                || !Objects.equals(view.getPolicyId(), fact.policyId())
                || !Objects.equals(view.getRetroactiveBillingBatchId(), fact.billingBatchId())
                || !Objects.equals(view.getRetroactiveBillingResultHash(), fact.sourceBatchResultHash())
                || !Objects.equals(view.getRetroactiveBillingResolutionId(), fact.billingResolutionId())
                || !Objects.equals(view.getRetroactivePeriodResolutionResultHash(), fact.resultHash())
                || fact.resolvedLineCount() != view.getRetroactiveBillingReviewCount()) {
            throw validation("retroactiveResolution", "Billing关闭期间处理权威结论与案件投影不一致");
        }
        validateResolutionLines(input, view, fact);
        return fact;
    }

    private void validateResolutionLines(
            MaintenanceEffectApplicationInput input,
            MaintenanceView view,
            ResolutionFact fact) {
        List<MaintenanceRetroactivePeriodAdjustmentView> reviewPeriods = periodAdjustmentViewRepository
                .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                        input.tenantId(), input.maintenanceId(), view.getRetroactivePeriodRecalculationId()).stream()
                .filter(period -> "CLOSED_PERIOD_REVIEW".equals(period.getBillingStatus()))
                .toList();
        if (reviewPeriods.size() != fact.resolvedLineCount()) {
            throw validation("retroactiveResolution", "关闭期间处理行数与当前期间投影不一致");
        }
        reviewPeriods.forEach(period -> fact.lines().stream()
                .filter(line -> Objects.equals(period.getPeriodId(), line.periodId()))
                .filter(line -> Objects.equals(period.getBillingResultHash(), line.sourceLineResultHash()))
                .findFirst()
                .orElseThrow(() -> validation(
                        "retroactiveResolution", "关闭期间处理明细摘要与当前期间投影不一致")));
    }

    private void validateExecutionCapabilities(
            MaintenanceEffectApplicationInput input,
            List<MaintenanceFieldChangeView> fields,
            java.time.LocalDate businessDate) {
        var catalog = policyFieldCatalogPort.getCatalog(
                new PolicyFieldCatalogRequest(input.tenantId(), null, null, businessDate));
        fields.forEach(field -> {
            var descriptor = catalog.requireField(field.getFieldCode());
            if (!descriptor.capability().executionSupported()) {
                throw validation("fieldCode", "字段尚未开放真实执行: " + field.getFieldCode());
            }
        });
    }

    private MaintenancePolicyApplicationEvidence toEvidence(
            ApplicationRequest request,
            ApplicationFact fact) {
        if (fact == null || !Objects.equals(request.requestId(), fact.requestId())
                || request.expectedPolicyVersion() != fact.expectedPolicyVersion()
                || fact.appliedSnapshot() == null || request.stateAction() != fact.stateAction()
                || !Objects.equals(request.retroactiveEvidence(), fact.retroactiveEvidence())) {
            throw validation("policyReceipt", "Policy 回执与请求事实不一致");
        }
        if (fact.stateAction().changesStatus()
                && (fact.statusBefore() == null || fact.statusBefore().isBlank()
                        || fact.statusAfter() == null || fact.statusAfter().isBlank())) {
            throw validation("policyReceipt", "状态类 Policy 回执缺少变更前后状态");
        }
        MaintenanceSnapshotReference snapshot = new MaintenanceSnapshotReference(
                fact.appliedSnapshot().storageKey(), fact.appliedSnapshot().contentHash(),
                fact.appliedSnapshot().policyVersion(), fact.appliedSnapshot().capturedAt());
        List<MaintenanceAppliedFieldEvidence> appliedFields = fact.appliedFields().stream()
                .map(field -> new MaintenanceAppliedFieldEvidence(
                        field.itemCode(), field.objectId(), field.fieldCode(),
                        requireDataType(field.dataType()), field.canonicalValue()))
                .toList();
        return new MaintenancePolicyApplicationEvidence(
                fact.requestId(), fact.endorsementNo(), fact.expectedPolicyVersion(),
                fact.actualPolicyVersion(), fact.applicationHash(), snapshot, appliedFields, fact.appliedAt(),
                fact.stateAction(), fact.statusBefore(), fact.statusAfter());
    }

    private void validateFrozenRequest(MaintenanceWorkflowTaskView task, ApplicationRequest request) {
        if (!Objects.equals(task.getEffectRequestId(), request.requestId())
                || !Objects.equals(task.getEffectRequestHash(), request.requestPayloadHash())
                || !Objects.equals(task.getEffectExpectedPolicyVersion(), request.expectedPolicyVersion())
                || !Objects.equals(task.getEffectTimeType(), EffectiveTimeType.fromCode(request.effectiveTimeType()))
                || !Objects.equals(task.getEffectRequestedEffectiveAt(), request.effectiveAt())
                || !Objects.equals(task.getEffectProposedSnapshotHash(), request.proposedSnapshotHash())) {
            throw validation("effectEvidence", "既有生效请求事实与重试载荷不一致");
        }
    }

    private LocalDateTime effectiveAt(MaintenanceWorkflowTaskView task) {
        return task.getEffectRequestedEffectiveAt() != null
                ? task.getEffectRequestedEffectiveAt() : LocalDateTime.now();
    }

    private java.time.LocalDate effectiveDate(LocalDateTime effectiveAt) {
        return effectiveAt.toLocalDate();
    }

    private long requireVersion(Long version) {
        if (version == null || version < 0) {
            throw validation("proposedPolicyVersion", "拟变更快照缺少有效 Policy 版本");
        }
        return version;
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isHash(String value) {
        return value != null && value.matches("[a-fA-F0-9]{64}");
    }

    private String requireHash(String value) {
        if (value == null || !value.matches("[a-fA-F0-9]{64}")) {
            throw validation("proposedSnapshotHash", "拟变更快照缺少合法 SHA-256 摘要");
        }
        return value.toLowerCase();
    }

    private PolicyFieldDataType requireDataType(String code) {
        for (PolicyFieldDataType dataType : PolicyFieldDataType.values()) {
            if (dataType.getCode().equals(code)) {
                return dataType;
            }
        }
        throw validation("dataType", "Policy 回执包含未知字段类型: " + code);
    }

    private String changeSummary(
            String maintenanceId,
            List<MaintenanceWorkflowTaskView> effectTasks,
            List<FieldChange> changes,
            PolicyMaintenanceAction stateAction) {
        String items = effectTasks.stream().map(MaintenanceWorkflowTaskView::getItemCode)
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        String fields = changes.stream().map(FieldChange::fieldCode).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(","));
        String summary = "maintenance=" + maintenanceId + ";items=" + items
                + ";fields=" + fields + ";action=" + stateAction.name();
        if (summary.length() > 512) {
            throw validation("changeSummary", "案件字段过多，无法形成稳定批单摘要");
        }
        return summary;
    }

    private PolicyMaintenanceAction stateAction(List<MaintenanceWorkflowTaskView> effectTasks) {
        Set<PolicyMaintenanceAction> actions = effectTasks.stream()
                .map(MaintenanceWorkflowTaskView::getItemCode)
                .map(this::stateAction)
                .filter(PolicyMaintenanceAction::changesStatus)
                .collect(java.util.stream.Collectors.toSet());
        if (actions.size() > 1) {
            throw validation("stateAction", "同一案件不能包含多个合同状态动作");
        }
        return actions.isEmpty() ? PolicyMaintenanceAction.NONE : actions.iterator().next();
    }

    private PolicyMaintenanceAction stateAction(String itemCode) {
        return MaintenanceItemCode.of(itemCode).legacyMaintenanceType().policyMaintenanceAction();
    }

    private TerminationReason terminationReason(
            List<MaintenanceWorkflowTaskView> effectTasks,
            PolicyMaintenanceAction action) {
        if (action != PolicyMaintenanceAction.TERMINATE) {
            return null;
        }
        boolean surrender = effectTasks.stream().map(MaintenanceWorkflowTaskView::getItemCode)
                .anyMatch(itemCode -> "SURRENDER".equals(itemCode) || "POLICY_SURRENDER".equals(itemCode));
        return surrender ? TerminationReason.WITHDRAWAL : TerminationReason.CONTRACT_TERMINATION;
    }

    private String stateReason(String maintenanceId, PolicyMaintenanceAction action) {
        return action.changesStatus() ? "保全案件 " + maintenanceId + " 执行 " + action.name() : null;
    }

    private String failureReason(RuntimeException exception) {
        String message = exception.getMessage();
        String reason = message == null || message.isBlank()
                ? exception.getClass().getSimpleName() : message;
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private CompletableFuture<Void> send(Object command) {
        return commandGateway.send(command).thenApply(ignored -> null);
    }

    private MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException("MaintenanceEffectApplicationService", field, message);
    }

    private record EffectContext(
            MaintenanceView caseView,
            List<MaintenanceWorkflowTaskView> effectTasks,
            MaintenanceSnapshotView snapshot,
            List<MaintenanceFieldChangeView> fields,
            RetroactiveEvidence retroactiveEvidence,
            MaintenanceFieldConflictOperationResult refreshedConflicts) {

        private EffectContext withRefreshedConflicts(MaintenanceFieldConflictOperationResult result) {
            return new EffectContext(caseView, effectTasks, snapshot, fields, retroactiveEvidence, result);
        }

        private long expectedPolicyVersion() {
            Long version = refreshedConflicts == null
                    ? snapshotPolicyVersion()
                    : refreshedConflicts.policyVersion();
            if (version == null || version < 0) {
                throw new MaintenanceValidationException(
                        "MaintenanceEffectApplicationService", "proposedPolicyVersion",
                        "拟变更快照缺少有效 Policy 版本");
            }
            return version;
        }

        private String proposedSnapshotHash() {
            String hash = refreshedConflicts == null
                    ? snapshotContentHash()
                    : refreshedConflicts.proposedSnapshotHash();
            if (hash == null || !hash.matches("[a-fA-F0-9]{64}")) {
                throw new MaintenanceValidationException(
                        "MaintenanceEffectApplicationService", "proposedSnapshotHash",
                        "拟变更快照缺少合法 SHA-256 摘要");
            }
            return hash.toLowerCase();
        }

        private Long snapshotPolicyVersion() {
            if (snapshot.getProposedPolicyVersion() != null || !fields.isEmpty()) {
                return snapshot.getProposedPolicyVersion();
            }
            return snapshot.getBeforePolicyVersion();
        }

        private String snapshotContentHash() {
            if (snapshot.getProposedContentHash() != null || !fields.isEmpty()) {
                return snapshot.getProposedContentHash();
            }
            return snapshot.getBeforeContentHash();
        }

        private List<FieldChange> applicationFieldChanges() {
            if (refreshedConflicts != null) {
                return refreshedConflicts.fieldChanges().stream()
                        .filter(change -> !change.currentValue().equals(change.proposedValue()))
                        .map(change -> new FieldChange(
                                change.itemCode(), change.objectId(), change.fieldCode(),
                                change.proposedValue().dataType().getCode(),
                                change.proposedValue().canonicalValue()))
                        .toList();
            }
            return fields.stream()
                    .filter(field -> !Objects.equals(field.getCurrentValue(), field.getProposedValue()))
                    .map(field -> new FieldChange(
                            field.getItemCode(), field.getObjectId(), field.getFieldCode(),
                            field.getDataType().getCode(), field.getProposedValue()))
                    .toList();
        }

        private List<String> taskIds() {
            return effectTasks.stream().map(MaintenanceWorkflowTaskView::getTaskId).sorted().toList();
        }

        private int retryCount() {
            return effectTasks.stream().mapToInt(MaintenanceWorkflowTaskView::getRetryCount).max().orElse(0);
        }
    }
}
