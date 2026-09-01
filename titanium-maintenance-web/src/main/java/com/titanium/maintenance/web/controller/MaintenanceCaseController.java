package com.titanium.maintenance.web.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.maintenance.application.command.casecreation.CreateMaintenanceCaseInput;
import com.titanium.maintenance.application.command.casecreation.MaintenanceAutomaticReviewInput;
import com.titanium.maintenance.application.command.casecreation.MaintenanceCaseCommandService;
import com.titanium.maintenance.application.command.casecreation.MaintenanceManualReviewInput;
import com.titanium.maintenance.application.command.casecreation.MaintenanceWorkflowTaskOperationInput;
import com.titanium.maintenance.application.command.effect.MaintenanceEffectApplicationInput;
import com.titanium.maintenance.application.command.effect.MaintenanceEffectScheduleOperationInput;
import com.titanium.maintenance.application.command.field.RecordMaintenanceFieldChangesInput;
import com.titanium.maintenance.application.command.field.RecordMaintenanceFieldChangesInput.FieldProposalInput;
import com.titanium.maintenance.application.command.field.RefreshMaintenanceFieldConflictsInput;
import com.titanium.maintenance.application.command.field.ResolveMaintenanceFieldConflictInput;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumQuoteInput;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumQuoteInput.UnderwritingAdjustmentInput;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumSettlementGateInput;
import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactiveImpactAnalysisInput;
import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactivePeriodRecalculationInput;
import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactivePeriodResolutionInput;
import com.titanium.maintenance.application.command.underwriting.MaintenanceUnderwritingAssessmentInput;
import com.titanium.maintenance.application.command.withdrawal.MaintenanceItemWithdrawalInput;
import com.titanium.maintenance.application.model.effect.MaintenanceEffectScheduleResult;
import com.titanium.maintenance.application.model.field.MaintenanceFieldConflictOperationResult;
import com.titanium.maintenance.application.query.MaintenanceCaseQueryApplicationService;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.query.query.MaintenanceCaseSearchCriteria;
import com.titanium.maintenance.web.dto.casecreation.AutomaticMaintenanceReviewDTO;
import com.titanium.maintenance.web.dto.casecreation.CompleteMaintenanceWorkflowTaskDTO;
import com.titanium.maintenance.web.dto.casecreation.CreateMaintenanceCaseDTO;
import com.titanium.maintenance.web.dto.casecreation.DecideMaintenanceReviewDTO;
import com.titanium.maintenance.web.dto.casecreation.DecideMaintenanceWorkflowConditionDTO;
import com.titanium.maintenance.web.dto.casecreation.FailMaintenanceWorkflowTaskDTO;
import com.titanium.maintenance.web.dto.casecreation.MaintenanceWorkflowOperationDTO;
import com.titanium.maintenance.web.dto.casecreation.RetryMaintenanceWorkflowTaskDTO;
import com.titanium.maintenance.web.dto.effect.ApplyMaintenanceEffectDTO;
import com.titanium.maintenance.web.dto.effect.MaintenanceEffectScheduleOperationDTO;
import com.titanium.maintenance.web.dto.field.RecordMaintenanceFieldChangesDTO;
import com.titanium.maintenance.web.dto.field.RefreshMaintenanceFieldConflictsDTO;
import com.titanium.maintenance.web.dto.field.ResolveMaintenanceFieldConflictDTO;
import com.titanium.maintenance.web.dto.premium.QuoteMaintenancePremiumDTO;
import com.titanium.maintenance.web.dto.premium.SettleMaintenancePremiumDTO;
import com.titanium.maintenance.web.dto.retroactive.AnalyzeMaintenanceRetroactiveImpactDTO;
import com.titanium.maintenance.web.dto.retroactive.RecalculateMaintenanceRetroactivePeriodsDTO;
import com.titanium.maintenance.web.dto.retroactive.ResolveMaintenanceRetroactivePeriodsDTO;
import com.titanium.maintenance.web.dto.withdrawal.WithdrawMaintenanceItemDTO;
import com.titanium.maintenance.web.mapper.MaintenanceCaseQueryWebMapper;
import com.titanium.maintenance.web.response.casecreation.MaintenanceAutomaticReviewVO;
import com.titanium.maintenance.web.response.casecreation.MaintenanceCaseCreationVO;
import com.titanium.maintenance.web.response.casecreation.MaintenanceCaseDetailVO;
import com.titanium.maintenance.web.response.casecreation.MaintenanceCasePageVO;
import com.titanium.maintenance.web.response.effect.MaintenanceEffectApplicationVO;
import com.titanium.maintenance.web.response.effect.MaintenanceEffectScheduleVO;
import com.titanium.maintenance.web.response.field.MaintenanceFieldConflictOperationVO;
import com.titanium.maintenance.web.response.premium.MaintenancePremiumQuoteVO;
import com.titanium.maintenance.web.response.premium.MaintenancePremiumSettlementGateVO;
import com.titanium.maintenance.web.response.retroactive.MaintenanceRetroactiveImpactAnalysisVO;
import com.titanium.maintenance.web.response.retroactive.MaintenanceRetroactivePeriodRecalculationVO;
import com.titanium.maintenance.web.response.retroactive.MaintenanceRetroactivePeriodResolutionVO;
import com.titanium.maintenance.web.response.underwriting.MaintenanceUnderwritingAssessmentVO;
import com.titanium.maintenance.web.response.withdrawal.MaintenanceItemWithdrawalVO;
import com.titanium.maintenance.web.security.MaintenanceCaseQueryAccessResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** 独立保全管理页面与系统 API 共用的建案 HTTP 入口。 */
@RestController
@Validated
@RequiredArgsConstructor
public class MaintenanceCaseController {

    private final MaintenanceCaseCommandService caseCommandService;
    private final MaintenanceCaseQueryApplicationService caseQueryService;
    private final MaintenanceCaseQueryWebMapper caseQueryWebMapper;
    private final MaintenanceCaseQueryAccessResolver queryAccessResolver;

    /** 独立案件列表；初始化未完成案件在查询层不可见。 */
    @GetMapping({"/api/v1/maintenance/cases", "/web/v1/maintenance/cases"})
    public ResponseEntity<MaintenanceCasePageVO> search(
            @RequestParam(required = false) @Size(max = 64) String caseId,
            @RequestParam(required = false) @Size(max = 64) String policyNumber,
            @RequestParam(required = false) @Size(max = 64) String customerId,
            @RequestParam(required = false) @Size(max = 64) String itemCode,
            @RequestParam(required = false) MaintenanceChannel source,
            @RequestParam(required = false) MaintenanceStatus status,
            @RequestParam(required = false) @Size(max = 64) String operatorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId) {
        MaintenanceCaseSearchCriteria criteria = new MaintenanceCaseSearchCriteria(
                caseId, policyNumber, customerId, itemCode, source, status, operatorId,
                createdFrom, createdTo, page, size);
        return ResponseEntity.ok(caseQueryWebMapper.toPageVO(caseQueryService.search(tenantId, criteria)));
    }

    /** 独立案件详情；敏感字段默认脱敏。 */
    @GetMapping({"/api/v1/maintenance/cases/{caseId}", "/web/v1/maintenance/cases/{caseId}"})
    public ResponseEntity<MaintenanceCaseDetailVO> detail(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId) {
        boolean sensitiveDetailsVisible = queryAccessResolver.sensitiveDetailsVisible();
        return ResponseEntity.ok(caseQueryWebMapper.toDetailVO(
                caseQueryService.findDetail(tenantId, caseId, sensitiveDetailsVisible)));
    }

    /** 触发四域权威追溯影响分析；请求体只接受幂等操作标识。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/retroactive-impact-analysis",
            "/web/v1/maintenance/cases/{caseId}/retroactive-impact-analysis"
    })
    public ResponseEntity<MaintenanceRetroactiveImpactAnalysisVO> analyzeRetroactiveImpact(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody AnalyzeMaintenanceRetroactiveImpactDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        var result = caseCommandService.analyzeRetroactiveImpact(new MaintenanceRetroactiveImpactAnalysisInput(
                caseId, request.operationId(), operatorId, tenantId));
        return ResponseEntity.ok(new MaintenanceRetroactiveImpactAnalysisVO(
                result.analysisId(), result.analysisVersion(), result.operationId(), result.status(),
                result.scopeFrom(), result.scopeTo(), result.itemCount(), result.blockingItemCount(),
                result.pendingItemCount(), result.resultHash(), result.failureCode(), result.failureMessage(),
                result.completedAt()));
    }

    /** 执行 Product 期间重算和 Billing 会计期间调整；请求体只接受幂等操作标识。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/retroactive-period-recalculation",
            "/web/v1/maintenance/cases/{caseId}/retroactive-period-recalculation"
    })
    public ResponseEntity<MaintenanceRetroactivePeriodRecalculationVO> recalculateRetroactivePeriods(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody RecalculateMaintenanceRetroactivePeriodsDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        var result = caseCommandService.recalculateRetroactivePeriods(
                new MaintenanceRetroactivePeriodRecalculationInput(
                        caseId, request.operationId(), operatorId, tenantId));
        return ResponseEntity.ok(new MaintenanceRetroactivePeriodRecalculationVO(
                result.periodRecalculationId(), result.periodRecalculationVersion(), result.operationId(),
                result.status(), result.analysisId(), result.analysisVersion(), result.analysisResultHash(),
                result.productRecalculationId(), result.direction(), result.amount(), result.currency(),
                result.periodCount(), result.billingBatchId(), result.billingStatus(), result.postedCount(),
                result.reviewCount(), result.failureCode(), result.failureMessage(), result.completedAt()));
    }

    /** 将全部关闭会计期间差额结转至指定开放期间。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/retroactive-period-resolution",
            "/web/v1/maintenance/cases/{caseId}/retroactive-period-resolution"
    })
    public ResponseEntity<MaintenanceRetroactivePeriodResolutionVO> resolveRetroactivePeriods(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody ResolveMaintenanceRetroactivePeriodsDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        var result = caseCommandService.resolveRetroactivePeriods(
                new MaintenanceRetroactivePeriodResolutionInput(
                        caseId, request.operationId(), request.targetAccountingPeriod(),
                        request.reason(), operatorId, tenantId));
        return ResponseEntity.ok(new MaintenanceRetroactivePeriodResolutionVO(
                result.periodResolutionId(), result.operationId(), result.status(),
                result.billingResolutionId(), result.billingBatchId(), result.sourceBatchResultHash(),
                result.targetAccountingPeriod(), result.resolvedLineCount(), result.resultHash(),
                result.reason(), result.failureCode(), result.failureMessage(), result.completedAt()));
    }

    /** 系统 API 自动建案；来源由路由固定，不接受请求体覆盖。 */
    @PostMapping("/api/v1/maintenance/cases")
    public CompletableFuture<ResponseEntity<MaintenanceCaseCreationVO>> createByApi(
            @Valid @RequestBody CreateMaintenanceCaseDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        return create(request, tenantId, operatorId, MaintenanceChannel.API);
    }

    /** 保全后台人工建案；来源由路由固定，不接受请求体覆盖。 */
    @PostMapping("/web/v1/maintenance/cases")
    public CompletableFuture<ResponseEntity<MaintenanceCaseCreationVO>> createManually(
            @Valid @RequestBody CreateMaintenanceCaseDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        return create(request, tenantId, operatorId, MaintenanceChannel.MANUAL);
    }

    /** 系统 API 保存字段草稿；敏感字段值不在响应中回显。 */
    @PutMapping("/api/v1/maintenance/cases/{caseId}/items/{itemCode}/changes")
    public CompletableFuture<ResponseEntity<Void>> recordApiFieldChanges(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 64) String itemCode,
            @Valid @RequestBody RecordMaintenanceFieldChangesDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        return recordFieldChanges(caseId, itemCode, request, tenantId, operatorId);
    }

    /** 保全后台保存字段草稿；与 API 使用相同领域命令。 */
    @PutMapping("/web/v1/maintenance/cases/{caseId}/items/{itemCode}/changes")
    public CompletableFuture<ResponseEntity<Void>> recordManualFieldChanges(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 64) String itemCode,
            @Valid @RequestBody RecordMaintenanceFieldChangesDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        return recordFieldChanges(caseId, itemCode, request, tenantId, operatorId);
    }

    /** 人工后台与系统 API 共用项目撤销编排，费用和资金结果均由服务端权威推导。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/items/{itemCode}/withdrawal",
            "/web/v1/maintenance/cases/{caseId}/items/{itemCode}/withdrawal"
    })
    public CompletableFuture<ResponseEntity<MaintenanceItemWithdrawalVO>> withdrawItem(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 64) String itemCode,
            @Valid @RequestBody WithdrawMaintenanceItemDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        return caseCommandService.withdrawItem(new MaintenanceItemWithdrawalInput(
                caseId, itemCode, request.operationId(), request.reason(), request.paymentMethod(),
                operatorId, tenantId)).thenApply(result -> ResponseEntity.ok(new MaintenanceItemWithdrawalVO(
                        result.itemCode(), result.operationId(), result.requestHash(), result.status(),
                        result.sourcePostingId(), result.sourceFundStatus(), result.reversalId(),
                        result.reversalResultHash(),
                        result.reversalDirection(), result.amount(), result.currency(), result.fundAction(),
                        result.fundStatus(), result.fundRequestId(), result.fundOrderId(),
                        result.fundExternalStatus(), result.failureCode(), result.failureMessage(),
                        result.retryCount(), result.requestedAt(), result.completedAt())));
    }

    /** 人工与系统任务领取共用同一领域命令，来源由匹配路由固定。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/claim",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/claim"})
    public CompletableFuture<ResponseEntity<Void>> claimTask(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody MaintenanceWorkflowOperationDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        return noContent(caseCommandService.claimTask(workflowInput(
                caseId, taskId, request.operationId(), null, null, null, null,
                null, tenantId, operatorId, source(servletRequest))));
    }

    /** 已领取任务开始处理。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/start",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/start"})
    public CompletableFuture<ResponseEntity<Void>> startTask(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody MaintenanceWorkflowOperationDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        return noContent(caseCommandService.startTask(workflowInput(
                caseId, taskId, request.operationId(), null, null, null, null,
                null, tenantId, operatorId, source(servletRequest))));
    }

    /** 完成信息录入或带权威证据的业务校验任务。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/complete",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/complete"})
    public CompletableFuture<ResponseEntity<Void>> completeTask(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody CompleteMaintenanceWorkflowTaskDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        return noContent(caseCommandService.completeTask(workflowInput(
                caseId, taskId, request.operationId(), request.evidenceVersion(),
                request.evidenceHash(), request.resultCode(), request.reason(), null,
                tenantId, operatorId, source(servletRequest))));
    }

    /** 记录处理中任务失败。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/fail",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/fail"})
    public CompletableFuture<ResponseEntity<Void>> failTask(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody FailMaintenanceWorkflowTaskDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        return noContent(caseCommandService.failTask(workflowInput(
                caseId, taskId, request.operationId(), null, null,
                request.failureCode(), request.failureReason(), null,
                tenantId, operatorId, source(servletRequest))));
    }

    /** 将失败任务恢复为可领取状态。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/retry",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/retry"})
    public CompletableFuture<ResponseEntity<Void>> retryTask(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody RetryMaintenanceWorkflowTaskDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        return noContent(caseCommandService.retryTask(workflowInput(
                caseId, taskId, request.operationId(), null, null, null,
                request.reason(), null, tenantId, operatorId, source(servletRequest))));
    }

    /** 条件结论只向系统 API 开放，人工路由不能直接覆盖规则结果。 */
    @PostMapping("/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/condition-decision")
    public CompletableFuture<ResponseEntity<Void>> decideTaskCondition(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody DecideMaintenanceWorkflowConditionDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        return noContent(caseCommandService.decideTaskCondition(workflowInput(
                caseId, taskId, request.operationId(), request.ruleVersion(),
                request.inputHash(), null, request.reason(), request.decision(),
                tenantId, operatorId, MaintenanceChannel.API)));
    }

    /** 后台人工审核；领域层继续校验领取人和任务状态。 */
    @PostMapping("/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/review-decision")
    public CompletableFuture<ResponseEntity<Void>> decideReview(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody DecideMaintenanceReviewDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        MaintenanceManualReviewInput input = new MaintenanceManualReviewInput(
                caseId, taskId, request.operationId(), request.decision(),
                request.policyVersion(), request.comment(), operatorId, tenantId,
                MaintenanceChannel.MANUAL);
        return noContent(caseCommandService.decideReview(input));
    }

    /** API 自动审核；任何门禁不满足时返回转人工，不写拒绝事实。 */
    @PostMapping("/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/auto-review")
    public CompletableFuture<ResponseEntity<MaintenanceAutomaticReviewVO>> automaticReview(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody AutomaticMaintenanceReviewDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        MaintenanceAutomaticReviewInput input = new MaintenanceAutomaticReviewInput(
                caseId, taskId, request.operationId(), request.policyVersion(),
                request.identityVerified(), request.identityEvidenceHash(),
                request.satisfiedMaterialCodes(), request.materialEvidenceHash(),
                request.amountWithinLimit(), request.amountEvidenceHash(),
                request.riskAccepted(), request.riskEvidenceHash(),
                operatorId, tenantId, MaintenanceChannel.API);
        return caseCommandService.automaticReview(input)
                .thenApply(result -> ResponseEntity.ok(new MaintenanceAutomaticReviewVO(
                        result.outcome(), result.policyCode(), result.policyVersion(), result.reasons())));
    }

    /** 人工后台与系统 API 通过同一编排触发立即生效，来源由路由固定。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/effect",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/effect"})
    public CompletableFuture<ResponseEntity<MaintenanceEffectApplicationVO>> applyEffect(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody ApplyMaintenanceEffectDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        MaintenanceEffectApplicationInput input = new MaintenanceEffectApplicationInput(
                caseId, taskId, request.operationId(), operatorId, tenantId, source(servletRequest));
        return caseCommandService.applyEffect(input).thenApply(result -> ResponseEntity.ok(
                new MaintenanceEffectApplicationVO(
                        result.requestId(), result.endorsementNo(), result.actualPolicyVersion(),
                        result.applicationHash(), result.appliedAt())));
    }

    /** 主动读取 Policy 最新快照并刷新案件全部顺序外字段冲突。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/field-conflicts/refresh",
            "/web/v1/maintenance/cases/{caseId}/field-conflicts/refresh"})
    public CompletableFuture<ResponseEntity<MaintenanceFieldConflictOperationVO>> refreshFieldConflicts(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody RefreshMaintenanceFieldConflictsDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        return caseCommandService.refreshFieldConflicts(new RefreshMaintenanceFieldConflictsInput(
                        caseId, request.operationId(), operatorId, tenantId))
                .thenApply(result -> ResponseEntity.ok(conflictOperationVO(result)));
    }

    /** 使用当前值、案件拟值或重新录入值显式解决单个字段冲突。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/field-conflicts/resolve",
            "/web/v1/maintenance/cases/{caseId}/field-conflicts/resolve"})
    public CompletableFuture<ResponseEntity<MaintenanceFieldConflictOperationVO>> resolveFieldConflict(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody ResolveMaintenanceFieldConflictDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId) {
        return caseCommandService.resolveFieldConflict(new ResolveMaintenanceFieldConflictInput(
                        caseId, request.operationId(), request.itemCode(), request.objectId(), request.fieldCode(),
                        request.action(), request.dataType(), request.canonicalValue(), request.reason(),
                        operatorId, tenantId))
                .thenApply(result -> ResponseEntity.ok(conflictOperationVO(result)));
    }

    /** 为历史未来生效案件幂等补建计划；新案件在建案完成后自动创建。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/effect-schedule",
            "/web/v1/maintenance/cases/{caseId}/effect-schedule"})
    public CompletableFuture<ResponseEntity<MaintenanceEffectScheduleVO>> createEffectSchedule(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody MaintenanceEffectScheduleOperationDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        return caseCommandService.createEffectSchedule(scheduleInput(
                        caseId, request, tenantId, operatorId, source(servletRequest)))
                .thenApply(result -> ResponseEntity.ok(scheduleVO(result)));
    }

    /** 暂停尚未执行的未来生效计划。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/effect-schedule/pause",
            "/web/v1/maintenance/cases/{caseId}/effect-schedule/pause"})
    public CompletableFuture<ResponseEntity<MaintenanceEffectScheduleVO>> pauseEffectSchedule(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody MaintenanceEffectScheduleOperationDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        return caseCommandService.pauseEffectSchedule(scheduleInput(
                        caseId, request, tenantId, operatorId, source(servletRequest)))
                .thenApply(result -> ResponseEntity.ok(scheduleVO(result)));
    }

    /** 恢复暂停或失败的未来生效计划。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/effect-schedule/resume",
            "/web/v1/maintenance/cases/{caseId}/effect-schedule/resume"})
    public CompletableFuture<ResponseEntity<MaintenanceEffectScheduleVO>> resumeEffectSchedule(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody MaintenanceEffectScheduleOperationDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        return caseCommandService.resumeEffectSchedule(scheduleInput(
                        caseId, request, tenantId, operatorId, source(servletRequest)))
                .thenApply(result -> ResponseEntity.ok(scheduleVO(result)));
    }

    /** 人工取得同一租约并立即触发权威重校验和正式生效。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/effect-schedule/execute-now",
            "/web/v1/maintenance/cases/{caseId}/effect-schedule/execute-now"})
    public ResponseEntity<MaintenanceEffectApplicationVO> executeEffectScheduleNow(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody MaintenanceEffectScheduleOperationDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        var result = caseCommandService.executeEffectScheduleNow(scheduleInput(
                caseId, request, tenantId, operatorId, source(servletRequest)));
        return ResponseEntity.ok(new MaintenanceEffectApplicationVO(
                result.requestId(), result.endorsementNo(), result.actualPolicyVersion(),
                result.applicationHash(), result.appliedAt()));
    }

    /** 人工后台和系统 API 均只能触发 Underwriting 权威评估，不能直接提交核保结论。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/underwriting-assessment",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/underwriting-assessment"})
    public CompletableFuture<ResponseEntity<MaintenanceUnderwritingAssessmentVO>> assessUnderwriting(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody MaintenanceWorkflowOperationDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        MaintenanceUnderwritingAssessmentInput input = new MaintenanceUnderwritingAssessmentInput(
                caseId, taskId, request.operationId(), operatorId, tenantId, source(servletRequest));
        return caseCommandService.assessUnderwriting(input)
                .thenApply(result -> ResponseEntity.ok(new MaintenanceUnderwritingAssessmentVO(
                        result.underwritingCaseId(), result.conclusion(), result.ruleVersion(),
                        result.modelVersion(), result.additionalConditions(), result.summary(),
                        result.completedAt())));
    }

    /** 人工后台和系统 API 均只提交定价输入，Product 负责形成最终报价事实。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/premium-quotes",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/premium-quotes"})
    public CompletableFuture<ResponseEntity<MaintenancePremiumQuoteVO>> quotePremium(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody QuoteMaintenancePremiumDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        MaintenancePremiumQuoteInput input = new MaintenancePremiumQuoteInput(
                caseId, taskId, request.operationId(), request.lifecycleType(),
                request.originalCalculationId(), request.currency(), request.sumInsured(), request.age(),
                request.gender(), request.paymentTermYears(), request.coverageTermYears(),
                request.paymentPeriods(), request.pricingFactors(),
                request.underwritingAdjustments().stream()
                        .map(item -> new UnderwritingAdjustmentInput(
                                item.adjustmentCode(), item.type(), item.value(),
                                item.reason(), item.ruleVersion()))
                        .toList(),
                request.channelId(), request.policyYear(), request.reason(), operatorId, tenantId,
                source(servletRequest));
        return caseCommandService.quotePremium(input)
                .thenApply(result -> ResponseEntity.ok(new MaintenancePremiumQuoteVO(
                        result.status(), result.quoteId(), result.quoteVersion(),
                        result.originalCalculationId(), result.replacementCalculationId(),
                        result.pricingPlanVersion(), result.resultHash(), result.detailSummary(),
                        result.direction(), result.amount(), result.currency(), result.quotedAt(),
                        result.validUntil())));
    }

    /** 人工后台和系统 API 共用 Billing 入账与 Payment 收退款门禁。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/premium-settlements",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/premium-settlements"})
    public CompletableFuture<ResponseEntity<MaintenancePremiumSettlementGateVO>> settlePremium(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody SettleMaintenancePremiumDTO request,
            @RequestHeader("X-Tenant-Id") @NotBlank @Size(max = 64) String tenantId,
            @RequestHeader("X-Operator-Id") @NotBlank @Size(max = 64) String operatorId,
            HttpServletRequest servletRequest) {
        MaintenancePremiumSettlementGateInput input = new MaintenancePremiumSettlementGateInput(
                caseId, taskId, request.operationId(), request.paymentMethod(), request.reason(),
                operatorId, tenantId, source(servletRequest));
        return caseCommandService.settlePremium(input)
                .thenApply(result -> ResponseEntity.ok(new MaintenancePremiumSettlementGateVO(
                        result.taskStatus(), result.postingId(), result.postingStatus(),
                        result.direction(), result.amount(), result.currency(), result.fundType(),
                        result.fundStatus(), result.instructionId(), result.orderId(),
                        result.externalStatus(), result.failureCode(), result.failureMessage(),
                        result.recordedAt())));
    }

    private CompletableFuture<ResponseEntity<MaintenanceCaseCreationVO>> create(
            CreateMaintenanceCaseDTO request,
            String tenantId,
            String operatorId,
            MaintenanceChannel source) {
        CreateMaintenanceCaseInput input = new CreateMaintenanceCaseInput(
                request.policyId(), request.resolvedItemCodes(), request.effectiveTimeType(),
                request.specificEffectiveDate(), request.description(), request.clientRequestKey(),
                source, operatorId, tenantId);
        return caseCommandService.create(input)
                .thenApply(maintenanceId -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new MaintenanceCaseCreationVO(maintenanceId)));
    }

    private MaintenanceFieldConflictOperationVO conflictOperationVO(
            MaintenanceFieldConflictOperationResult result) {
        return new MaintenanceFieldConflictOperationVO(
                result.operationId(), result.policyVersion(), result.proposedSnapshotHash(), result.conflictCount());
    }

    private MaintenanceEffectScheduleOperationInput scheduleInput(
            String caseId,
            MaintenanceEffectScheduleOperationDTO request,
            String tenantId,
            String operatorId,
            MaintenanceChannel source) {
        return new MaintenanceEffectScheduleOperationInput(
                caseId, request.operationId(), request.reason(), operatorId, tenantId, source);
    }

    private MaintenanceEffectScheduleVO scheduleVO(
            MaintenanceEffectScheduleResult result) {
        return new MaintenanceEffectScheduleVO(
                result.scheduleId(), result.effectiveTimeType(), result.status(), result.tenantZoneId(),
                result.nextExecutionAt(), result.attemptCount(), result.lastAttemptId(), result.lastAttemptAt(),
                result.lastErrorCode(), result.lastErrorMessage());
    }

    private CompletableFuture<ResponseEntity<Void>> recordFieldChanges(
            String caseId,
            String itemCode,
            RecordMaintenanceFieldChangesDTO request,
            String tenantId,
            String operatorId) {
        List<FieldProposalInput> proposals = request.proposals().stream()
                .map(proposal -> new FieldProposalInput(
                        proposal.objectId(), proposal.fieldCode(), proposal.dataType(), proposal.canonicalValue()))
                .toList();
        RecordMaintenanceFieldChangesInput input = new RecordMaintenanceFieldChangesInput(
                caseId, itemCode, proposals, operatorId, tenantId);
        return caseCommandService.recordFieldChanges(input)
                .thenApply(ignored -> ResponseEntity.noContent().build());
    }

    private MaintenanceWorkflowTaskOperationInput workflowInput(
            String caseId,
            String taskId,
            String operationId,
            String evidenceVersion,
            String evidenceHash,
            String resultCode,
            String reason,
            MaintenanceWorkflowConditionDecision decision,
            String tenantId,
            String operatorId,
            MaintenanceChannel source) {
        return new MaintenanceWorkflowTaskOperationInput(
                caseId, taskId, operationId, evidenceVersion, evidenceHash,
                resultCode, reason, decision, operatorId, tenantId, source);
    }

    private MaintenanceChannel source(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/")
                ? MaintenanceChannel.API
                : MaintenanceChannel.MANUAL;
    }

    private CompletableFuture<ResponseEntity<Void>> noContent(CompletableFuture<Void> operation) {
        return operation.thenApply(ignored -> ResponseEntity.noContent().build());
    }
}
