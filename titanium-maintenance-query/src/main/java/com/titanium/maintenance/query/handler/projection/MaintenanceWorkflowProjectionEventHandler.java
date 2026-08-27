package com.titanium.maintenance.query.handler.projection;

import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;

import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.event.MaintenanceEffectStatusChangedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowInitializedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowTaskTransitionedEvent;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;

import lombok.RequiredArgsConstructor;

/** 将案件工作流初始化事实投影为租户隔离的任务列表。 */
@Component
@ProcessingGroup("maintenance-query-group")
@RequiredArgsConstructor
public class MaintenanceWorkflowProjectionEventHandler {

    private final MaintenanceWorkflowTaskViewRepository repository;

    @EventHandler
    @Transactional
    public void on(MaintenanceWorkflowInitializedEvent event) {
        List<MaintenanceWorkflowTaskView> views = event.tasks().stream()
                .map(task -> toView(event, task))
                .toList();
        repository.saveAll(views);
    }

    /** 按事件中的完整后值更新当前任务及被激活的后继任务。 */
    @EventHandler
    @Transactional
    public void on(MaintenanceWorkflowTaskTransitionedEvent event) {
        update(event, event.afterTask());
        if (event.activatedTaskAfter() != null) {
            update(event, event.activatedTaskAfter());
        }
    }

    /** Policy 已完成案件级应用时，自动关闭配置中的终结标记任务。 */
    @EventHandler
    @Transactional
    public void on(MaintenanceEffectStatusChangedEvent event) {
        if (event.currentStatus() != MaintenanceEffectStatus.APPLIED) {
            return;
        }
        List<MaintenanceWorkflowTaskView> terminalTasks = repository
                .findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                        event.tenantId(), event.maintenanceId().id())
                .stream()
                .filter(task -> task.getStepType() == MaintenanceStepType.COMPLETE)
                .filter(task -> task.getStatus() != MaintenanceWorkflowTaskStatus.SKIPPED)
                .toList();
        terminalTasks.forEach(task -> {
            task.setStatus(MaintenanceWorkflowTaskStatus.COMPLETED);
            task.setUpdateTime(event.changedAt());
        });
        repository.saveAll(terminalTasks);
    }

    private MaintenanceWorkflowTaskView toView(
            MaintenanceWorkflowInitializedEvent event,
            MaintenanceWorkflowTask task) {
        MaintenanceWorkflowTaskView view = new MaintenanceWorkflowTaskView();
        view.setTaskId(task.taskId());
        view.setMaintenanceId(event.maintenanceId().id());
        view.setItemCode(task.itemCode());
        view.setItemOrder(task.itemOrder());
        view.setSequence(task.sequence());
        view.setStepType(task.stepType());
        view.setMode(task.mode());
        view.setConditionRuleCode(task.conditionRuleCode());
        apply(view, task);
        view.setTenantId(event.tenantId());
        view.setCreateTime(event.initializedAt());
        view.setUpdateTime(event.initializedAt());
        return view;
    }

    private void update(
            MaintenanceWorkflowTaskTransitionedEvent event,
            MaintenanceWorkflowTask task) {
        MaintenanceWorkflowTaskView view = repository
                .findByTenantIdAndMaintenanceIdAndTaskId(
                        event.tenantId(), event.maintenanceId().id(), task.taskId())
                .orElseThrow(() -> new IllegalStateException(
                        "流程任务投影不存在: " + task.taskId()));
        apply(view, task);
        view.setUpdateTime(event.transitionedAt());
        repository.save(view);
    }

    private void apply(MaintenanceWorkflowTaskView view, MaintenanceWorkflowTask task) {
        view.setStatus(task.status());
        view.setAssignedTo(task.assignment() == null ? null : task.assignment().assignee());
        view.setClaimedAt(task.assignment() == null ? null : task.assignment().claimedAt());
        view.setRetryCount(task.retryCount());
        view.setFailureCode(task.failure() == null ? null : task.failure().failureCode());
        view.setFailureReason(task.failure() == null ? null : task.failure().failureReason());
        view.setConditionRuleVersion(
                task.conditionEvidence() == null ? null : task.conditionEvidence().ruleVersion());
        view.setConditionInputHash(
                task.conditionEvidence() == null ? null : task.conditionEvidence().inputHash());
        view.setConditionDecision(
                task.conditionEvidence() == null ? null : task.conditionEvidence().decision());
        view.setConditionReason(
                task.conditionEvidence() == null ? null : task.conditionEvidence().reason());
        view.setConditionDecidedAt(
                task.conditionEvidence() == null ? null : task.conditionEvidence().decidedAt());
        view.setConditionDecidedBy(
                task.conditionEvidence() == null ? null : task.conditionEvidence().decidedBy());
        view.setReviewMode(task.reviewEvidence() == null ? null : task.reviewEvidence().mode());
        view.setReviewDecision(task.reviewEvidence() == null ? null : task.reviewEvidence().decision());
        view.setReviewPolicyCode(task.reviewEvidence() == null ? null : task.reviewEvidence().policyCode());
        view.setReviewPolicyVersion(
                task.reviewEvidence() == null ? null : task.reviewEvidence().policyVersion());
        view.setReviewContextHash(
                task.reviewEvidence() == null ? null : task.reviewEvidence().contentHash());
        view.setReviewGateEvidenceJson(
                task.reviewEvidence() == null ? null : JSON.toJSONString(task.reviewEvidence().gates()));
        view.setReviewComment(task.reviewEvidence() == null ? null : task.reviewEvidence().comment());
        view.setReviewDecidedAt(task.reviewEvidence() == null ? null : task.reviewEvidence().decidedAt());
        view.setReviewDecidedBy(task.reviewEvidence() == null ? null : task.reviewEvidence().decidedBy());
        view.setUnderwritingCaseId(
                task.underwritingEvidence() == null ? null : task.underwritingEvidence().underwritingCaseId());
        view.setUnderwritingRequestHash(
                task.underwritingEvidence() == null ? null : task.underwritingEvidence().requestPayloadHash());
        view.setUnderwritingRuleVersion(
                task.underwritingEvidence() == null ? null : task.underwritingEvidence().ruleVersion());
        view.setUnderwritingModelVersion(
                task.underwritingEvidence() == null ? null : task.underwritingEvidence().modelVersion());
        view.setUnderwritingConclusion(
                task.underwritingEvidence() == null ? null : task.underwritingEvidence().conclusion());
        view.setUnderwritingConditionsJson(
                task.underwritingEvidence() == null
                        ? null
                        : JSON.toJSONString(task.underwritingEvidence().additionalConditions()));
        view.setUnderwritingSummary(
                task.underwritingEvidence() == null ? null : task.underwritingEvidence().summary());
        view.setUnderwritingCompletedAt(
                task.underwritingEvidence() == null ? null : task.underwritingEvidence().completedAt());
        view.setPremiumQuoteStatus(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().status());
        view.setPremiumQuoteId(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().quoteId());
        view.setPremiumQuoteVersion(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().quoteVersion());
        view.setPremiumQuoteRequestHash(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().requestPayloadHash());
        view.setPremiumQuoteOriginalCalculationId(
                task.premiumQuoteEvidence() == null
                        ? null
                        : task.premiumQuoteEvidence().originalCalculationId());
        view.setPremiumQuoteOriginalResultHash(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().originalResultHash());
        view.setPremiumQuoteReplacementCalculationId(
                task.premiumQuoteEvidence() == null
                        ? null
                        : task.premiumQuoteEvidence().replacementCalculationId());
        view.setPremiumQuoteReplacementResultHash(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().replacementResultHash());
        view.setPremiumQuotePricingPlanVersion(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().pricingPlanVersion());
        view.setPremiumQuotePricingPlanHash(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().pricingPlanContentHash());
        view.setPremiumQuoteResultHash(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().resultHash());
        view.setPremiumQuoteDetailSummary(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().detailSummary());
        view.setPremiumQuoteDirection(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().direction());
        view.setPremiumQuoteAmount(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().amount());
        view.setPremiumQuoteCurrency(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().currency());
        view.setPremiumQuotedAt(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().quotedAt());
        view.setPremiumQuoteValidUntil(
                task.premiumQuoteEvidence() == null ? null : task.premiumQuoteEvidence().validUntil());
        view.setBillingPostingId(
                task.billingPostingEvidence() == null ? null : task.billingPostingEvidence().postingId());
        view.setBillingAdjustmentId(
                task.billingPostingEvidence() == null ? null : task.billingPostingEvidence().adjustmentId());
        view.setBillingResultHash(
                task.billingPostingEvidence() == null ? null : task.billingPostingEvidence().resultHash());
        view.setBillingPostingDirection(
                task.billingPostingEvidence() == null ? null : task.billingPostingEvidence().direction());
        view.setBillingPostingAmount(
                task.billingPostingEvidence() == null ? null : task.billingPostingEvidence().amount());
        view.setBillingPostingCurrency(
                task.billingPostingEvidence() == null ? null : task.billingPostingEvidence().currency());
        view.setBillingPostingStatus(
                task.billingPostingEvidence() == null ? null : task.billingPostingEvidence().status());
        view.setBillingCommissionAdjustmentCount(
                task.billingPostingEvidence() == null
                        ? null
                        : task.billingPostingEvidence().commissionAdjustmentCount());
        view.setBillingPostedAt(
                task.billingPostingEvidence() == null ? null : task.billingPostingEvidence().recordedAt());
        view.setFundSettlementType(
                task.fundSettlementEvidence() == null ? null : task.fundSettlementEvidence().type());
        view.setFundSettlementStatus(
                task.fundSettlementEvidence() == null ? null : task.fundSettlementEvidence().status());
        view.setFundSourcePostingId(
                task.fundSettlementEvidence() == null
                        ? null
                        : task.fundSettlementEvidence().sourcePostingId());
        view.setFundSettlementInstructionId(
                task.fundSettlementEvidence() == null ? null : task.fundSettlementEvidence().instructionId());
        view.setFundSettlementOrderId(
                task.fundSettlementEvidence() == null ? null : task.fundSettlementEvidence().orderId());
        view.setFundSettlementExternalStatus(
                task.fundSettlementEvidence() == null ? null : task.fundSettlementEvidence().externalStatus());
        view.setFundSettlementAmount(
                task.fundSettlementEvidence() == null ? null : task.fundSettlementEvidence().amount());
        view.setFundSettlementCurrency(
                task.fundSettlementEvidence() == null ? null : task.fundSettlementEvidence().currency());
        view.setFundSettlementFailureCode(
                task.fundSettlementEvidence() == null ? null : task.fundSettlementEvidence().failureCode());
        view.setFundSettlementFailureMessage(
                task.fundSettlementEvidence() == null ? null : task.fundSettlementEvidence().failureMessage());
        view.setFundSettlementRecordedAt(
                task.fundSettlementEvidence() == null ? null : task.fundSettlementEvidence().recordedAt());
        var effect = task.effectEvidence();
        var effectRequest = effect == null ? null : effect.request();
        var policyApplication = effect == null ? null : effect.application();
        view.setEffectRequestId(effectRequest == null ? null : effectRequest.requestId());
        view.setEffectRequestHash(effectRequest == null ? null : effectRequest.requestPayloadHash());
        view.setEffectExpectedPolicyVersion(
                effectRequest == null ? null : effectRequest.expectedPolicyVersion());
        view.setEffectTimeType(effectRequest == null ? null : effectRequest.effectiveTimeType());
        view.setEffectRequestedEffectiveAt(
                effectRequest == null ? null : effectRequest.requestedEffectiveAt());
        view.setEffectProposedSnapshotHash(
                effectRequest == null ? null : effectRequest.proposedSnapshotHash());
        view.setEffectRequestedAt(effectRequest == null ? null : effectRequest.requestedAt());
        view.setPolicyEndorsementNo(
                policyApplication == null ? null : policyApplication.endorsementNo());
        view.setPolicyActualVersion(
                policyApplication == null ? null : policyApplication.actualPolicyVersion());
        view.setPolicyApplicationHash(
                policyApplication == null ? null : policyApplication.applicationHash());
        view.setPolicyStateAction(
                policyApplication == null ? null : policyApplication.stateAction());
        view.setPolicyStatusBefore(
                policyApplication == null ? null : policyApplication.statusBefore());
        view.setPolicyStatusAfter(
                policyApplication == null ? null : policyApplication.statusAfter());
        view.setAppliedSnapshotStorageKey(
                policyApplication == null ? null : policyApplication.appliedSnapshot().storageKey());
        view.setAppliedSnapshotHash(
                policyApplication == null ? null : policyApplication.appliedSnapshot().contentHash());
        view.setAppliedSnapshotPolicyVersion(
                policyApplication == null ? null : policyApplication.appliedSnapshot().policyVersion());
        view.setAppliedSnapshotCapturedAt(
                policyApplication == null ? null : policyApplication.appliedSnapshot().capturedAt().toString());
        view.setAppliedFieldsJson(
                policyApplication == null ? null : JSON.toJSONString(policyApplication.appliedFields()));
        view.setPolicyAppliedAt(
                policyApplication == null ? null : policyApplication.appliedAt());
        view.setLastOperationId(
                task.lastOperation() == null ? null : task.lastOperation().operationId());
        view.setLastOperationAction(
                task.lastOperation() == null ? null : task.lastOperation().action());
        view.setLastOperationHash(
                task.lastOperation() == null ? null : task.lastOperation().payloadHash());
        view.setLastEvidenceVersion(
                task.lastOperation() == null ? null : task.lastOperation().evidenceVersion());
        view.setLastEvidenceHash(
                task.lastOperation() == null ? null : task.lastOperation().evidenceHash());
        view.setLastResultCode(
                task.lastOperation() == null ? null : task.lastOperation().resultCode());
        view.setLastOperationReason(
                task.lastOperation() == null ? null : task.lastOperation().reason());
        view.setLastOperatedAt(
                task.lastOperation() == null ? null : task.lastOperation().operatedAt());
        view.setLastOperatedBy(
                task.lastOperation() == null ? null : task.lastOperation().operatedBy());
    }
}
