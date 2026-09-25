package com.titanium.maintenance.web.controller.cases;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.maintenance.application.command.casecreation.MaintenanceAutomaticReviewInput;
import com.titanium.maintenance.application.command.casecreation.MaintenanceCaseCommandService;
import com.titanium.maintenance.application.command.casecreation.MaintenanceDocumentIssueInput;
import com.titanium.maintenance.application.command.casecreation.MaintenanceManualReviewInput;
import com.titanium.maintenance.application.command.casecreation.MaintenanceWorkflowTaskOperationInput;
import com.titanium.maintenance.application.command.withdrawal.MaintenanceItemWithdrawalInput;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.web.dto.casecreation.AutomaticMaintenanceReviewDTO;
import com.titanium.maintenance.web.dto.casecreation.CompleteMaintenanceWorkflowTaskDTO;
import com.titanium.maintenance.web.dto.casecreation.DecideMaintenanceReviewDTO;
import com.titanium.maintenance.web.dto.casecreation.DecideMaintenanceWorkflowConditionDTO;
import com.titanium.maintenance.web.dto.casecreation.FailMaintenanceWorkflowTaskDTO;
import com.titanium.maintenance.web.dto.casecreation.IssueMaintenanceDocumentDTO;
import com.titanium.maintenance.web.dto.casecreation.MaintenanceWorkflowOperationDTO;
import com.titanium.maintenance.web.dto.casecreation.RetryMaintenanceWorkflowTaskDTO;
import com.titanium.maintenance.web.dto.withdrawal.WithdrawMaintenanceItemDTO;
import com.titanium.maintenance.web.response.casecreation.MaintenanceAutomaticReviewVO;
import com.titanium.maintenance.web.response.withdrawal.MaintenanceItemWithdrawalVO;
import com.titanium.metadata.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * 保全案件任务流转入口：任务的撤回/领取/开始/完成/失败/重试/条件裁决、复核、单证出具与自动复核。
 *
 * <p>由原 {@code MaintenanceCaseController} 按业务主题拆分而来，端点 URL 与拆分前逐字一致。</p>
 */

@RestController
@Validated
@RequiredArgsConstructor
public class MaintenanceCaseTaskController extends MaintenanceCaseControllerSupport {

    private final MaintenanceCaseCommandService caseCommandService;

    /** 人工后台与系统 API 共用项目撤销编排，费用和资金结果均由服务端权威推导。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/items/{itemCode}/withdrawal",
            "/web/v1/maintenance/cases/{caseId}/items/{itemCode}/withdrawal"
    })
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceItemWithdrawalVO>>> withdrawItem(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 64) String itemCode,
            @Valid @RequestBody WithdrawMaintenanceItemDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return caseCommandService.withdrawItem(new MaintenanceItemWithdrawalInput(
                caseId, itemCode, request.operationId(), request.reason(), request.paymentMethod(),
                operatorId, tenantId)).thenApply(result -> ResponseEntity.ok(ApiResponse.success(new MaintenanceItemWithdrawalVO(
                        result.itemCode(), result.operationId(), result.requestHash(), result.status(),
                        result.sourcePostingId(), result.sourceFundStatus(), result.reversalId(),
                        result.reversalResultHash(),
                        result.reversalDirection(), result.amount(), result.currency(), result.fundAction(),
                        result.fundStatus(), result.fundRequestId(), result.fundOrderId(),
                        result.fundExternalStatus(), result.failureCode(), result.failureMessage(),
                        result.retryCount(), result.requestedAt(), result.completedAt()))));
    }

    /** 人工与系统任务领取共用同一领域命令，来源由匹配路由固定。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/claim",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/claim"})
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> claimTask(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody MaintenanceWorkflowOperationDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return noContent(caseCommandService.claimTask(workflowInput(
                caseId, taskId, request.operationId(), null, null, null, null,
                null, tenantId, operatorId, source(servletRequest))));
    }

    /** 已领取任务开始处理。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/start",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/start"})
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> startTask(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody MaintenanceWorkflowOperationDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return noContent(caseCommandService.startTask(workflowInput(
                caseId, taskId, request.operationId(), null, null, null, null,
                null, tenantId, operatorId, source(servletRequest))));
    }

    /** 完成信息录入或带权威证据的业务校验任务。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/complete",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/complete"})
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> completeTask(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody CompleteMaintenanceWorkflowTaskDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return noContent(caseCommandService.completeTask(workflowInput(
                caseId, taskId, request.operationId(), request.evidenceVersion(),
                request.evidenceHash(), request.resultCode(), request.reason(), null,
                tenantId, operatorId, source(servletRequest))));
    }

    /** 记录处理中任务失败。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/fail",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/fail"})
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> failTask(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody FailMaintenanceWorkflowTaskDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return noContent(caseCommandService.failTask(workflowInput(
                caseId, taskId, request.operationId(), null, null,
                request.failureCode(), request.failureReason(), null,
                tenantId, operatorId, source(servletRequest))));
    }

    /** 将失败任务恢复为可领取状态。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/retry",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/retry"})
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> retryTask(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody RetryMaintenanceWorkflowTaskDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return noContent(caseCommandService.retryTask(workflowInput(
                caseId, taskId, request.operationId(), null, null, null,
                request.reason(), null, tenantId, operatorId, source(servletRequest))));
    }

    /** 条件结论只向系统 API 开放，人工路由不能直接覆盖规则结果。 */
    @PostMapping("/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/condition-decision")
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> decideTaskCondition(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody DecideMaintenanceWorkflowConditionDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return noContent(caseCommandService.decideTaskCondition(workflowInput(
                caseId, taskId, request.operationId(), request.ruleVersion(),
                request.inputHash(), null, request.reason(), request.decision(),
                tenantId, operatorId, MaintenanceChannel.API)));
    }

    /** 后台人工审核；领域层继续校验领取人和任务状态。 */
    @PostMapping("/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/review-decision")
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> decideReview(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody DecideMaintenanceReviewDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        MaintenanceManualReviewInput input = new MaintenanceManualReviewInput(
                caseId, taskId, request.operationId(), request.decision(),
                request.policyVersion(), request.comment(), operatorId, tenantId,
                MaintenanceChannel.MANUAL);
        return noContent(caseCommandService.decideReview(input));
    }

    /** 后台出具保全凭证；模板编码取自案件冻结配置，领域层继续校验领取人和任务状态。 */
    @PostMapping("/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/document-issue")
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> issueDocument(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody IssueMaintenanceDocumentDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        MaintenanceDocumentIssueInput input = new MaintenanceDocumentIssueInput(
                caseId, taskId, request.operationId(), request.voucherNo(), operatorId, tenantId,
                MaintenanceChannel.MANUAL);
        return noContent(caseCommandService.issueDocument(input));
    }

    /** 后台完成案件终结标记步骤；前序任务终态由领域层按同项目步骤序判定。 */
    @PostMapping("/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/complete-item")
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> completeItem(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody MaintenanceWorkflowOperationDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return noContent(caseCommandService.completeItem(workflowInput(
                caseId, taskId, request.operationId(), null, null, null, null, null,
                tenantId, operatorId, MaintenanceChannel.MANUAL)));
    }

    /** API 自动审核；任何门禁不满足时返回转人工，不写拒绝事实。 */
    @PostMapping("/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/auto-review")
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceAutomaticReviewVO>>> automaticReview(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody AutomaticMaintenanceReviewDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        MaintenanceAutomaticReviewInput input = new MaintenanceAutomaticReviewInput(
                caseId, taskId, request.operationId(), request.policyVersion(),
                request.identityVerified(), request.identityEvidenceHash(),
                request.satisfiedMaterialCodes(), request.materialEvidenceHash(),
                request.amountWithinLimit(), request.amountEvidenceHash(),
                request.riskAccepted(), request.riskEvidenceHash(),
                operatorId, tenantId, MaintenanceChannel.API);
        return caseCommandService.automaticReview(input)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(new MaintenanceAutomaticReviewVO(
                        result.outcome(), result.policyCode(), result.policyVersion(), result.reasons()))));
    }

    private CompletableFuture<ResponseEntity<ApiResponse<Void>>> noContent(CompletableFuture<Void> operation) {
        return operation.thenApply(ignored -> ResponseEntity.ok(ApiResponse.success()));
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
}
