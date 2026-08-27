package com.titanium.maintenance.application.orchestration.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Objects;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.MaintenanceRetroactivePeriodResolutionInput;
import com.titanium.maintenance.application.model.MaintenanceRetroactivePeriodResolutionResult;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.BillingRetroactivePeriodResolutionPort;
import com.titanium.maintenance.port.BillingRetroactivePeriodResolutionPort.ResolutionFact;
import com.titanium.maintenance.port.BillingRetroactivePeriodResolutionPort.ResolutionRequest;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolutionEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolutionLine;

import lombok.RequiredArgsConstructor;

/** 编排关闭会计期间差额结转并保存 Billing 权威处理结论。 */
@Service
@RequiredArgsConstructor
public class MaintenanceRetroactivePeriodResolutionApplicationService {

    private static final String FAILURE_CODE = "RETROACTIVE_PERIOD_RESOLUTION_FAILED";

    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final BillingRetroactivePeriodResolutionPort billingPort;

    public MaintenanceRetroactivePeriodResolutionResult resolve(
            MaintenanceRetroactivePeriodResolutionInput input) {
        validate(input);
        MaintenanceView view = maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        if (sameOperation(view, input.operationId())
                && view.getRetroactivePeriodResolutionStatus()
                        == MaintenanceRetroactivePeriodResolutionStatus.COMPLETED) {
            return existingResult(view);
        }
        requireReady(view, input.operationId());

        YearMonth targetPeriod = parsePeriod(input.targetAccountingPeriod());
        String requestId = stableId("mrr", input.tenantId(), input.maintenanceId(), input.operationId());
        ResolutionRequest request = new ResolutionRequest(
                input.tenantId(), input.maintenanceId(), view.getPolicyId(),
                view.getRetroactiveBillingBatchId(), view.getRetroactiveBillingResultHash(),
                requestId, targetPeriod, input.reason(), input.operatorId());
        String resolutionId = sameOperation(view, input.operationId())
                ? view.getRetroactivePeriodResolutionId()
                : stableId("resolution", input.tenantId(), input.maintenanceId(), input.operationId());
        commandGateway.sendAndWait(new StartMaintenanceRetroactivePeriodResolutionCommand(
                MaintenanceId.of(input.maintenanceId()), resolutionId, input.operationId(), request.payloadHash(),
                view.getRetroactiveBillingBatchId(), view.getRetroactiveBillingResultHash(),
                targetPeriod.toString(), input.reason(), LocalDateTime.now(), input.operatorId()));

        MaintenanceRetroactivePeriodResolutionEvidence evidence;
        try {
            evidence = toEvidence(request, billingPort.resolve(request));
        } catch (RuntimeException exception) {
            String message = safeMessage(exception);
            commandGateway.sendAndWait(new FailMaintenanceRetroactivePeriodResolutionCommand(
                    MaintenanceId.of(input.maintenanceId()), resolutionId, input.operationId(),
                    FAILURE_CODE, message, LocalDateTime.now(), input.operatorId()));
            return new MaintenanceRetroactivePeriodResolutionResult(
                    resolutionId, input.operationId(), MaintenanceRetroactivePeriodResolutionStatus.FAILED,
                    null, view.getRetroactiveBillingBatchId(), view.getRetroactiveBillingResultHash(),
                    targetPeriod.toString(), 0, null, input.reason(), FAILURE_CODE, message, LocalDateTime.now());
        }
        commandGateway.sendAndWait(new CompleteMaintenanceRetroactivePeriodResolutionCommand(
                MaintenanceId.of(input.maintenanceId()), resolutionId, input.operationId(), evidence,
                LocalDateTime.now(), input.operatorId()));
        return completedResult(resolutionId, input.operationId(), evidence);
    }

    private MaintenanceRetroactivePeriodResolutionEvidence toEvidence(
            ResolutionRequest request,
            ResolutionFact fact) {
        if (fact == null || !Objects.equals(request.resolutionRequestId(), fact.resolutionRequestId())
                || !Objects.equals(request.billingBatchId(), fact.billingBatchId())
                || !Objects.equals(request.tenantId(), fact.tenantId())
                || !Objects.equals(request.maintenanceId(), fact.maintenanceId())
                || !Objects.equals(request.policyId(), fact.policyId())
                || !Objects.equals(request.sourceBatchResultHash(), fact.sourceBatchResultHash())
                || !Objects.equals(request.targetAccountingPeriod(), fact.targetAccountingPeriod())
                || !Objects.equals(request.reason(), fact.reason())
                || !Objects.equals(request.operatorId(), fact.resolvedBy())
                || !Objects.equals("COMPLETED", fact.status())) {
            throw validation("billingResolution", "Billing关闭期间处理结论与请求不一致");
        }
        return new MaintenanceRetroactivePeriodResolutionEvidence(
                fact.billingResolutionId(), fact.resolutionRequestId(), fact.billingBatchId(),
                fact.sourceBatchResultHash(), fact.targetAccountingPeriod().toString(),
                fact.resolvedLineCount(), fact.requestHash(), fact.resultHash(), fact.reason(),
                fact.resolvedBy(), fact.resolvedAt(), fact.lines().stream()
                        .map(line -> new MaintenanceRetroactivePeriodResolutionLine(
                                line.periodId(), line.sourceAccountingPeriod().toString(),
                                line.targetAccountingPeriod().toString(), line.direction(),
                                line.differenceAmount(), line.currency(), line.postingReference(),
                                line.sourceLineResultHash(), line.resultHash()))
                        .toList());
    }

    private void requireReady(MaintenanceView view, String operationId) {
        if (view.getEffectiveTimeType() != EffectiveTimeType.RETROACTIVE
                || view.getRetroactiveImpactStatus() != MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED) {
            throw validation("analysis", "必须先完成追溯影响分析");
        }
        if (view.getRetroactivePeriodRecalculationStatus()
                        != MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED
                || view.getRetroactiveBillingReviewCount() < 1
                || !hasText(view.getRetroactiveBillingBatchId())
                || !isHash(view.getRetroactiveBillingResultHash())) {
            throw validation("periodRecalculation", "当前案件没有可处理的关闭会计期间差额");
        }
        if (view.getRetroactivePeriodResolutionStatus()
                        == MaintenanceRetroactivePeriodResolutionStatus.COMPLETED
                && !sameOperation(view, operationId)) {
            throw validation("periodResolution", "关闭会计期间差额已经处理完成");
        }
        if (view.getRetroactivePeriodResolutionStatus()
                        == MaintenanceRetroactivePeriodResolutionStatus.RESOLVING
                && !sameOperation(view, operationId)) {
            throw validation("periodResolution", "已有关闭会计期间处理正在执行");
        }
    }

    private MaintenanceRetroactivePeriodResolutionResult existingResult(MaintenanceView view) {
        return new MaintenanceRetroactivePeriodResolutionResult(
                view.getRetroactivePeriodResolutionId(), view.getRetroactivePeriodResolutionOperationId(),
                view.getRetroactivePeriodResolutionStatus(), view.getRetroactiveBillingResolutionId(),
                view.getRetroactiveBillingBatchId(), view.getRetroactivePeriodResolutionSourceBatchHash(),
                view.getRetroactivePeriodResolutionTargetPeriod(),
                view.getRetroactivePeriodResolutionResolvedLineCount(),
                view.getRetroactivePeriodResolutionResultHash(), view.getRetroactivePeriodResolutionReason(),
                view.getRetroactivePeriodResolutionFailureCode(),
                view.getRetroactivePeriodResolutionFailureMessage(),
                view.getRetroactivePeriodResolutionCompletedAt());
    }

    private MaintenanceRetroactivePeriodResolutionResult completedResult(
            String resolutionId,
            String operationId,
            MaintenanceRetroactivePeriodResolutionEvidence evidence) {
        return new MaintenanceRetroactivePeriodResolutionResult(
                resolutionId, operationId, MaintenanceRetroactivePeriodResolutionStatus.COMPLETED,
                evidence.billingResolutionId(), evidence.billingBatchId(), evidence.sourceBatchResultHash(),
                evidence.targetAccountingPeriod(), evidence.resolvedLineCount(), evidence.resultHash(),
                evidence.reason(), null, null, evidence.resolvedAt());
    }

    private boolean sameOperation(MaintenanceView view, String operationId) {
        return Objects.equals(view.getRetroactivePeriodResolutionOperationId(), operationId);
    }

    private void validate(MaintenanceRetroactivePeriodResolutionInput input) {
        if (input == null) {
            throw validation("input", "关闭期间处理请求不能为空");
        }
        requireText("maintenanceId", input.maintenanceId());
        requireText("operationId", input.operationId());
        requireText("targetAccountingPeriod", input.targetAccountingPeriod());
        requireText("reason", input.reason());
        requireText("operatorId", input.operatorId());
        requireText("tenantId", input.tenantId());
    }

    private YearMonth parsePeriod(String value) {
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw validation("targetAccountingPeriod", "目标会计期间必须为yyyy-MM");
        }
    }

    private String stableId(String prefix, String... values) {
        return prefix + '-' + sha256(String.join("|", values)).substring(0, 32);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM不支持SHA-256", exception);
        }
    }

    private String requireText(String field, String value) {
        if (!hasText(value)) {
            throw validation(field, "字段不能为空");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isHash(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException(
                "MaintenanceRetroactivePeriodResolutionApplicationService", field, message);
    }
}
