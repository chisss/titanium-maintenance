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
import com.titanium.maintenance.application.command.effect.MaintenanceEffectApplicationInput;
import com.titanium.maintenance.application.command.effect.MaintenanceEffectScheduleOperationInput;
import com.titanium.maintenance.application.command.field.RefreshMaintenanceFieldConflictsInput;
import com.titanium.maintenance.application.command.field.ResolveMaintenanceFieldConflictInput;
import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactiveImpactAnalysisInput;
import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactivePeriodRecalculationInput;
import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactivePeriodResolutionInput;
import com.titanium.maintenance.application.model.effect.MaintenanceEffectScheduleResult;
import com.titanium.maintenance.application.model.effect.MaintenanceInvestmentSwitchInput;
import com.titanium.maintenance.application.model.field.MaintenanceFieldConflictOperationResult;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.web.dto.effect.ApplyMaintenanceEffectDTO;
import com.titanium.maintenance.web.dto.effect.MaintenanceEffectScheduleOperationDTO;
import com.titanium.maintenance.web.dto.field.RefreshMaintenanceFieldConflictsDTO;
import com.titanium.maintenance.web.dto.field.ResolveMaintenanceFieldConflictDTO;
import com.titanium.maintenance.web.dto.retroactive.AnalyzeMaintenanceRetroactiveImpactDTO;
import com.titanium.maintenance.web.dto.retroactive.RecalculateMaintenanceRetroactivePeriodsDTO;
import com.titanium.maintenance.web.dto.retroactive.ResolveMaintenanceRetroactivePeriodsDTO;
import com.titanium.maintenance.web.response.effect.MaintenanceEffectApplicationVO;
import com.titanium.maintenance.web.response.effect.MaintenanceEffectScheduleVO;
import com.titanium.maintenance.web.response.field.MaintenanceFieldConflictOperationVO;
import com.titanium.maintenance.web.response.retroactive.MaintenanceRetroactiveImpactAnalysisVO;
import com.titanium.maintenance.web.response.retroactive.MaintenanceRetroactivePeriodRecalculationVO;
import com.titanium.maintenance.web.response.retroactive.MaintenanceRetroactivePeriodResolutionVO;
import com.titanium.metadata.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * 保全案件生效与追溯入口：生效申请、字段冲突刷新/裁定、生效计划调度，以及追溯影响分析/重算/裁定。
 *
 * <p>由原 {@code MaintenanceCaseController} 按业务主题拆分而来，端点 URL 与拆分前逐字一致。</p>
 */

@RestController
@Validated
@RequiredArgsConstructor
public class MaintenanceCaseEffectController extends MaintenanceCaseControllerSupport {

    private final MaintenanceCaseCommandService caseCommandService;

    /** 人工后台与系统 API 通过同一编排触发立即生效，来源由路由固定。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/tasks/{taskId}/effect",
            "/web/v1/maintenance/cases/{caseId}/tasks/{taskId}/effect"})
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceEffectApplicationVO>>> applyEffect(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 191) String taskId,
            @Valid @RequestBody ApplyMaintenanceEffectDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        MaintenanceEffectApplicationInput input = new MaintenanceEffectApplicationInput(
                caseId, taskId, request.operationId(), operatorId, tenantId, source(servletRequest),
                switchInput(request.investmentSwitch()));
        return caseCommandService.applyEffect(input).thenApply(result -> ResponseEntity.ok(ApiResponse.success(
                new MaintenanceEffectApplicationVO(
                        result.requestId(), result.endorsementNo(), result.actualPolicyVersion(),
                        result.applicationHash(), result.appliedAt()))));
    }

    /** 主动读取 Policy 最新快照并刷新案件全部顺序外字段冲突。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/field-conflicts/refresh",
            "/web/v1/maintenance/cases/{caseId}/field-conflicts/refresh"})
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceFieldConflictOperationVO>>> refreshFieldConflicts(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody RefreshMaintenanceFieldConflictsDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return caseCommandService.refreshFieldConflicts(new RefreshMaintenanceFieldConflictsInput(
                        caseId, request.operationId(), operatorId, tenantId))
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(conflictOperationVO(result))));
    }

    /** 使用当前值、案件拟值或重新录入值显式解决单个字段冲突。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/field-conflicts/resolve",
            "/web/v1/maintenance/cases/{caseId}/field-conflicts/resolve"})
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceFieldConflictOperationVO>>> resolveFieldConflict(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody ResolveMaintenanceFieldConflictDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return caseCommandService.resolveFieldConflict(new ResolveMaintenanceFieldConflictInput(
                        caseId, request.operationId(), request.itemCode(), request.objectId(), request.fieldCode(),
                        request.action(), request.dataType(), request.canonicalValue(), request.reason(),
                        operatorId, tenantId))
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(conflictOperationVO(result))));
    }

    /** 为历史未来生效案件幂等补建计划；新案件在建案完成后自动创建。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/effect-schedule",
            "/web/v1/maintenance/cases/{caseId}/effect-schedule"})
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceEffectScheduleVO>>> createEffectSchedule(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody MaintenanceEffectScheduleOperationDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return caseCommandService.createEffectSchedule(scheduleInput(
                        caseId, request, tenantId, operatorId, source(servletRequest)))
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(scheduleVO(result))));
    }

    /** 暂停尚未执行的未来生效计划。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/effect-schedule/pause",
            "/web/v1/maintenance/cases/{caseId}/effect-schedule/pause"})
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceEffectScheduleVO>>> pauseEffectSchedule(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody MaintenanceEffectScheduleOperationDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return caseCommandService.pauseEffectSchedule(scheduleInput(
                        caseId, request, tenantId, operatorId, source(servletRequest)))
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(scheduleVO(result))));
    }

    /** 恢复暂停或失败的未来生效计划。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/effect-schedule/resume",
            "/web/v1/maintenance/cases/{caseId}/effect-schedule/resume"})
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceEffectScheduleVO>>> resumeEffectSchedule(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @Valid @RequestBody MaintenanceEffectScheduleOperationDTO request,
            HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return caseCommandService.resumeEffectSchedule(scheduleInput(
                        caseId, request, tenantId, operatorId, source(servletRequest)))
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success(scheduleVO(result))));
    }

    /** 人工取得同一租约并立即触发权威重校验和正式生效。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/effect-schedule/execute-now",
            "/web/v1/maintenance/cases/{caseId}/effect-schedule/execute-now"})
    public ApiResponse<MaintenanceEffectApplicationVO> executeEffectScheduleNow(
         @PathVariable @NotBlank @Size(max = 64) String caseId,
         @Valid @RequestBody MaintenanceEffectScheduleOperationDTO request,
         HttpServletRequest servletRequest) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        var result = caseCommandService.executeEffectScheduleNow(scheduleInput(
                caseId, request, tenantId, operatorId, source(servletRequest)));
        return ApiResponse.success(new MaintenanceEffectApplicationVO(
                result.requestId(), result.endorsementNo(), result.actualPolicyVersion(),
                result.applicationHash(), result.appliedAt()));
    }

    /** 触发四域权威追溯影响分析；请求体只接受幂等操作标识。 */
    @PostMapping({
            "/api/v1/maintenance/cases/{caseId}/retroactive-impact-analysis",
            "/web/v1/maintenance/cases/{caseId}/retroactive-impact-analysis"
    })
    public ApiResponse<MaintenanceRetroactiveImpactAnalysisVO> analyzeRetroactiveImpact(
         @PathVariable @NotBlank @Size(max = 64) String caseId,
         @Valid @RequestBody AnalyzeMaintenanceRetroactiveImpactDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        var result = caseCommandService.analyzeRetroactiveImpact(new MaintenanceRetroactiveImpactAnalysisInput(
                caseId, request.operationId(), operatorId, tenantId));
        return ApiResponse.success(new MaintenanceRetroactiveImpactAnalysisVO(
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
    public ApiResponse<MaintenanceRetroactivePeriodRecalculationVO> recalculateRetroactivePeriods(
         @PathVariable @NotBlank @Size(max = 64) String caseId,
         @Valid @RequestBody RecalculateMaintenanceRetroactivePeriodsDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        var result = caseCommandService.recalculateRetroactivePeriods(
                new MaintenanceRetroactivePeriodRecalculationInput(
                        caseId, request.operationId(), operatorId, tenantId));
        return ApiResponse.success(new MaintenanceRetroactivePeriodRecalculationVO(
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
    public ApiResponse<MaintenanceRetroactivePeriodResolutionVO> resolveRetroactivePeriods(
         @PathVariable @NotBlank @Size(max = 64) String caseId,
         @Valid @RequestBody ResolveMaintenanceRetroactivePeriodsDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        var result = caseCommandService.resolveRetroactivePeriods(
                new MaintenanceRetroactivePeriodResolutionInput(
                        caseId, request.operationId(), request.targetAccountingPeriod(),
                        request.reason(), operatorId, tenantId));
        return ApiResponse.success(new MaintenanceRetroactivePeriodResolutionVO(
                result.periodResolutionId(), result.operationId(), result.status(),
                result.billingResolutionId(), result.billingBatchId(), result.sourceBatchResultHash(),
                result.targetAccountingPeriod(), result.resolvedLineCount(), result.resultHash(),
                result.reason(), result.failureCode(), result.failureMessage(), result.completedAt()));
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

    /** 账户转换参数翻译：未传（非账户转换案件）时为 null，交由应用层按案件类型判定必要性。 */
    private MaintenanceInvestmentSwitchInput switchInput(ApplyMaintenanceEffectDTO.InvestmentSwitchDTO parameters) {
        if (parameters == null) {
            return null;
        }
        return new MaintenanceInvestmentSwitchInput(parameters.switchOutUnits(), parameters.targetUnitPrice(),
                parameters.targetCurrency(), parameters.targetFund());
    }
}
