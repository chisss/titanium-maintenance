package com.titanium.maintenance.web.controller.cases;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.maintenance.application.command.casecreation.MaintenanceCaseCommandService;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumQuoteInput;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumQuoteInput.UnderwritingAdjustmentInput;
import com.titanium.maintenance.application.command.premium.MaintenancePremiumSettlementGateInput;
import com.titanium.maintenance.application.command.underwriting.MaintenanceUnderwritingAssessmentInput;
import com.titanium.maintenance.web.dto.casecreation.MaintenanceWorkflowOperationDTO;
import com.titanium.maintenance.web.dto.premium.QuoteMaintenancePremiumDTO;
import com.titanium.maintenance.web.dto.premium.SettleMaintenancePremiumDTO;
import com.titanium.maintenance.web.response.premium.MaintenancePremiumQuoteVO;
import com.titanium.maintenance.web.response.premium.MaintenancePremiumSettlementGateVO;
import com.titanium.maintenance.web.response.underwriting.MaintenanceUnderwritingAssessmentVO;
import com.titanium.metadata.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * 保全案件评估入口：核保评估、保费试算与保费结算前置校验。
 *
 * <p>由原 {@code MaintenanceCaseController} 按业务主题拆分而来，端点 URL 与拆分前逐字一致。</p>
 */

@RestController
@Validated
@RequiredArgsConstructor
public class MaintenanceCaseAssessmentController extends MaintenanceCaseControllerSupport {

    private final MaintenanceCaseCommandService caseCommandService;

    /** 人工后台和系统 API 均只能触发 Underwriting 权威评估，不能直接提交核保结论。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/underwriting-assessment",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/underwriting-assessment"})
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceUnderwritingAssessmentVO>>> assessUnderwriting(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody MaintenanceWorkflowOperationDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        MaintenanceUnderwritingAssessmentInput input = new MaintenanceUnderwritingAssessmentInput(
                caseId, taskId, request.operationId(), operatorId, tenantId, source(servletRequest));
        return caseCommandService.assessUnderwriting(input)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(new MaintenanceUnderwritingAssessmentVO(
                        result.underwritingCaseId(), result.conclusion(), result.ruleVersion(),
                        result.modelVersion(), result.additionalConditions(), result.summary(),
                        result.completedAt()))));
    }

    /** 人工后台和系统 API 均只提交定价输入，Product 负责形成最终报价事实。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/premium-quotes",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/premium-quotes"})
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenancePremiumQuoteVO>>> quotePremium(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody QuoteMaintenancePremiumDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
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
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(new MaintenancePremiumQuoteVO(
                        result.status(), result.quoteId(), result.quoteVersion(),
                        result.originalCalculationId(), result.replacementCalculationId(),
                        result.pricingPlanVersion(), result.resultHash(), result.detailSummary(),
                        result.direction(), result.amount(), result.currency(), result.quotedAt(),
                        result.validUntil()))));
    }

    /** 人工后台和系统 API 共用 Billing 入账与 Payment 收退款门禁。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/premium-settlements",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/premium-settlements"})
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenancePremiumSettlementGateVO>>> settlePremium(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody SettleMaintenancePremiumDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        MaintenancePremiumSettlementGateInput input = new MaintenancePremiumSettlementGateInput(
                caseId, taskId, request.operationId(), request.paymentMethod(), request.reason(),
                operatorId, tenantId, source(servletRequest));
        return caseCommandService.settlePremium(input)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(new MaintenancePremiumSettlementGateVO(
                        result.taskStatus(), result.postingId(), result.postingStatus(),
                        result.direction(), result.amount(), result.currency(), result.fundType(),
                        result.fundStatus(), result.instructionId(), result.orderId(),
                        result.externalStatus(), result.failureCode(), result.failureMessage(),
                        result.recordedAt()))));
    }
}
