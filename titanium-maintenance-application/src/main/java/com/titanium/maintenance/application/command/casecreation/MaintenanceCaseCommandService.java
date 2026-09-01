package com.titanium.maintenance.application.command.casecreation;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.effect.MaintenanceEffectApplicationInput;
import com.titanium.maintenance.application.command.effect.MaintenanceEffectScheduleOperationInput;
import com.titanium.maintenance.application.command.field.RecordMaintenanceFieldChangesInput;
import com.titanium.maintenance.application.command.field.RefreshMaintenanceFieldConflictsInput;
import com.titanium.maintenance.application.command.field.ResolveMaintenanceFieldConflictInput;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumQuoteInput;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumSettlementGateInput;
import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactiveImpactAnalysisInput;
import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactivePeriodRecalculationInput;
import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactivePeriodResolutionInput;
import com.titanium.maintenance.application.command.underwriting.MaintenanceUnderwritingAssessmentInput;
import com.titanium.maintenance.application.command.withdrawal.MaintenanceItemWithdrawalInput;
import com.titanium.maintenance.application.model.casecreation.MaintenanceAutomaticReviewResult;
import com.titanium.maintenance.application.model.effect.MaintenanceEffectApplicationResult;
import com.titanium.maintenance.application.model.effect.MaintenanceEffectScheduleResult;
import com.titanium.maintenance.application.model.field.MaintenanceFieldConflictOperationResult;
import com.titanium.maintenance.application.model.premium.MaintenancePremiumQuoteResult;
import com.titanium.maintenance.application.model.premium.MaintenancePremiumSettlementGateResult;
import com.titanium.maintenance.application.model.retroactive.MaintenanceRetroactiveImpactAnalysisResult;
import com.titanium.maintenance.application.model.retroactive.MaintenanceRetroactivePeriodRecalculationResult;
import com.titanium.maintenance.application.model.retroactive.MaintenanceRetroactivePeriodResolutionResult;
import com.titanium.maintenance.application.model.underwriting.MaintenanceUnderwritingAssessmentResult;
import com.titanium.maintenance.application.model.withdrawal.MaintenanceItemWithdrawalResult;
import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceCaseCreationApplicationService;
import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceCaseCreationRequest;
import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceFieldDraftApplicationService;
import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceFieldDraftRequest;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceEffectApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceEffectScheduleApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceFieldConflictApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceItemWithdrawalApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenancePremiumSettlementApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceRetroactiveImpactAnalysisApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceRetroactivePeriodRecalculationApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceRetroactivePeriodResolutionApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceWorkflowApplicationService;

import lombok.RequiredArgsConstructor;

/** 独立保全建案的应用层写门面。 */
@Service
@RequiredArgsConstructor
public class MaintenanceCaseCommandService {

    private final MaintenanceCaseCreationApplicationService caseCreationApplicationService;
    private final MaintenanceFieldDraftApplicationService fieldDraftApplicationService;
    private final MaintenanceWorkflowApplicationService workflowApplicationService;
    private final MaintenanceEffectApplicationService effectApplicationService;
    private final MaintenanceEffectScheduleApplicationService effectScheduleApplicationService;
    private final MaintenanceFieldConflictApplicationService fieldConflictApplicationService;
    private final MaintenanceItemWithdrawalApplicationService itemWithdrawalApplicationService;
    private final MaintenancePremiumSettlementApplicationService premiumSettlementApplicationService;
    private final MaintenanceRetroactiveImpactAnalysisApplicationService retroactiveImpactAnalysisApplicationService;
    private final MaintenanceRetroactivePeriodRecalculationApplicationService
            retroactivePeriodRecalculationApplicationService;
    private final MaintenanceRetroactivePeriodResolutionApplicationService
            retroactivePeriodResolutionApplicationService;

    /** 将入站写请求交给快照优先的内部建案编排。 */
    public CompletableFuture<String> create(CreateMaintenanceCaseInput input) {
        return caseCreationApplicationService.create(new MaintenanceCaseCreationRequest(
                input.policyId(), input.itemCodes(), input.effectiveTimeType(),
                input.specificEffectiveDate(), input.description(), input.clientRequestKey(),
                input.source(), input.operatorId(), input.tenantId()));
    }

    /** 保存某一已冻结保全项的完整结构化字段草稿。 */
    public CompletableFuture<Void> recordFieldChanges(RecordMaintenanceFieldChangesInput input) {
        return fieldDraftApplicationService.record(new MaintenanceFieldDraftRequest(
                input.maintenanceId(), input.itemCode(), input.proposals(),
                input.operatorId(), input.tenantId()));
    }

    public CompletableFuture<Void> claimTask(MaintenanceWorkflowTaskOperationInput input) {
        return workflowApplicationService.claim(input);
    }

    public CompletableFuture<Void> startTask(MaintenanceWorkflowTaskOperationInput input) {
        return workflowApplicationService.start(input);
    }

    public CompletableFuture<Void> completeTask(MaintenanceWorkflowTaskOperationInput input) {
        return workflowApplicationService.complete(input);
    }

    public CompletableFuture<Void> failTask(MaintenanceWorkflowTaskOperationInput input) {
        return workflowApplicationService.fail(input);
    }

    public CompletableFuture<Void> retryTask(MaintenanceWorkflowTaskOperationInput input) {
        return workflowApplicationService.retry(input);
    }

    public CompletableFuture<Void> decideTaskCondition(MaintenanceWorkflowTaskOperationInput input) {
        return workflowApplicationService.decideCondition(input);
    }

    public CompletableFuture<Void> decideReview(MaintenanceManualReviewInput input) {
        return workflowApplicationService.decideReview(input);
    }

    public CompletableFuture<MaintenanceAutomaticReviewResult> automaticReview(
            MaintenanceAutomaticReviewInput input) {
        return workflowApplicationService.automaticReview(input);
    }

    public CompletableFuture<MaintenanceUnderwritingAssessmentResult> assessUnderwriting(
            MaintenanceUnderwritingAssessmentInput input) {
        return workflowApplicationService.assessUnderwriting(input);
    }

    public CompletableFuture<MaintenancePremiumQuoteResult> quotePremium(
            MaintenancePremiumQuoteInput input) {
        return workflowApplicationService.quotePremium(input);
    }

    public CompletableFuture<MaintenancePremiumSettlementGateResult>
            settlePremium(MaintenancePremiumSettlementGateInput input) {
        return premiumSettlementApplicationService.settle(input);
    }

    public CompletableFuture<MaintenanceEffectApplicationResult> applyEffect(
            MaintenanceEffectApplicationInput input) {
        return effectApplicationService.apply(input);
    }

    public CompletableFuture<MaintenanceFieldConflictOperationResult> refreshFieldConflicts(
            RefreshMaintenanceFieldConflictsInput input) {
        return fieldConflictApplicationService.refresh(input);
    }

    public CompletableFuture<MaintenanceFieldConflictOperationResult> resolveFieldConflict(
            ResolveMaintenanceFieldConflictInput input) {
        return fieldConflictApplicationService.resolve(input);
    }

    /** 撤销单个保全项目并完成必要的逆向财务补偿。 */
    public CompletableFuture<MaintenanceItemWithdrawalResult> withdrawItem(
            MaintenanceItemWithdrawalInput input) {
        return itemWithdrawalApplicationService.withdraw(input);
    }

    public CompletableFuture<MaintenanceEffectScheduleResult> createEffectSchedule(
            MaintenanceEffectScheduleOperationInput input) {
        return effectScheduleApplicationService.create(input);
    }

    public CompletableFuture<MaintenanceEffectScheduleResult> pauseEffectSchedule(
            MaintenanceEffectScheduleOperationInput input) {
        return effectScheduleApplicationService.pause(input);
    }

    public CompletableFuture<MaintenanceEffectScheduleResult> resumeEffectSchedule(
            MaintenanceEffectScheduleOperationInput input) {
        return effectScheduleApplicationService.resume(input);
    }

    public MaintenanceEffectApplicationResult executeEffectScheduleNow(
            MaintenanceEffectScheduleOperationInput input) {
        return effectScheduleApplicationService.executeNow(input);
    }

    public MaintenanceRetroactiveImpactAnalysisResult analyzeRetroactiveImpact(
            MaintenanceRetroactiveImpactAnalysisInput input) {
        return retroactiveImpactAnalysisApplicationService.analyze(input);
    }

    public MaintenanceRetroactivePeriodRecalculationResult recalculateRetroactivePeriods(
            MaintenanceRetroactivePeriodRecalculationInput input) {
        return retroactivePeriodRecalculationApplicationService.recalculate(input);
    }

    /** 处理关闭会计期间差额并保存 Billing 权威结论。 */
    public MaintenanceRetroactivePeriodResolutionResult resolveRetroactivePeriods(
            MaintenanceRetroactivePeriodResolutionInput input) {
        return retroactivePeriodResolutionApplicationService.resolve(input);
    }
}
