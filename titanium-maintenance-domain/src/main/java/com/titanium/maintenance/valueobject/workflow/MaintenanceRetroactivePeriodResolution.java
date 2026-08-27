package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;
import java.util.Locale;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 案件级关闭会计期间处理状态机。 */
public record MaintenanceRetroactivePeriodResolution(
        String periodResolutionId,
        String operationId,
        String requestHash,
        String billingBatchId,
        String sourceBatchResultHash,
        String targetAccountingPeriod,
        String reason,
        MaintenanceRetroactivePeriodResolutionStatus status,
        MaintenanceRetroactivePeriodResolutionEvidence evidence,
        String failureCode,
        String failureMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt) {

    public MaintenanceRetroactivePeriodResolution {
        periodResolutionId = text(periodResolutionId, "periodResolutionId");
        operationId = text(operationId, "operationId");
        requestHash = hash(requestHash, "requestHash");
        billingBatchId = text(billingBatchId, "billingBatchId");
        sourceBatchResultHash = hash(sourceBatchResultHash, "sourceBatchResultHash");
        targetAccountingPeriod = period(targetAccountingPeriod);
        reason = text(reason, "reason");
        failureCode = normalize(failureCode);
        failureMessage = normalize(failureMessage);
        if (status == null || startedAt == null || updatedAt == null) {
            throw invalid("处理状态和时间不能为空");
        }
        validateState();
    }

    public static MaintenanceRetroactivePeriodResolution start(
            String id,
            String operationId,
            String requestHash,
            String billingBatchId,
            String sourceBatchResultHash,
            String targetAccountingPeriod,
            String reason,
            LocalDateTime startedAt) {
        return new MaintenanceRetroactivePeriodResolution(
                id, operationId, requestHash, billingBatchId, sourceBatchResultHash,
                targetAccountingPeriod, reason, MaintenanceRetroactivePeriodResolutionStatus.RESOLVING,
                null, null, null, startedAt, null, startedAt);
    }

    public MaintenanceRetroactivePeriodResolution complete(
            MaintenanceRetroactivePeriodResolutionEvidence result,
            LocalDateTime completedTime) {
        if (evidence != null) {
            if (evidence.resultHash().equals(result.resultHash())) {
                return this;
            }
            throw invalid("同一关闭期间处理已存在不同Billing结果");
        }
        if (status != MaintenanceRetroactivePeriodResolutionStatus.RESOLVING
                && status != MaintenanceRetroactivePeriodResolutionStatus.FAILED) {
            throw invalid("当前状态不能记录关闭期间处理结果");
        }
        if (!billingBatchId.equals(result.billingBatchId())
                || !sourceBatchResultHash.equals(result.sourceBatchResultHash())
                || !targetAccountingPeriod.equals(result.targetAccountingPeriod())
                || !reason.equals(result.reason())) {
            throw invalid("Billing处理结论与冻结请求不一致");
        }
        LocalDateTime time = resultTime(completedTime);
        return new MaintenanceRetroactivePeriodResolution(
                periodResolutionId, operationId, requestHash, billingBatchId, sourceBatchResultHash,
                targetAccountingPeriod, reason, MaintenanceRetroactivePeriodResolutionStatus.COMPLETED,
                result, null, null, startedAt, time, time);
    }

    public MaintenanceRetroactivePeriodResolution fail(
            String code,
            String message,
            LocalDateTime failedAt) {
        if (status == MaintenanceRetroactivePeriodResolutionStatus.COMPLETED) {
            throw invalid("已完成关闭期间处理不能改为失败");
        }
        String normalizedCode = text(code, "failureCode");
        String normalizedMessage = text(message, "failureMessage");
        if (status == MaintenanceRetroactivePeriodResolutionStatus.FAILED
                && normalizedCode.equals(failureCode) && normalizedMessage.equals(failureMessage)) {
            return this;
        }
        LocalDateTime time = resultTime(failedAt);
        return new MaintenanceRetroactivePeriodResolution(
                periodResolutionId, operationId, requestHash, billingBatchId, sourceBatchResultHash,
                targetAccountingPeriod, reason, MaintenanceRetroactivePeriodResolutionStatus.FAILED,
                null, normalizedCode, normalizedMessage, startedAt, time, time);
    }

    public boolean sameRequest(String candidateOperationId, String candidateRequestHash) {
        return operationId.equals(text(candidateOperationId, "operationId"))
                && requestHash.equals(hash(candidateRequestHash, "requestHash"));
    }

    private void validateState() {
        if (status == MaintenanceRetroactivePeriodResolutionStatus.RESOLVING
                && (evidence != null || failureCode != null || failureMessage != null || completedAt != null)) {
            throw invalid("处理中状态不能携带终态信息");
        }
        if (status == MaintenanceRetroactivePeriodResolutionStatus.COMPLETED
                && (evidence == null || failureCode != null || failureMessage != null || completedAt == null)) {
            throw invalid("处理完成状态缺少Billing结论");
        }
        if (status == MaintenanceRetroactivePeriodResolutionStatus.FAILED
                && (evidence != null || failureCode == null || failureMessage == null || completedAt == null)) {
            throw invalid("处理失败状态必须只携带失败事实");
        }
    }

    private LocalDateTime resultTime(LocalDateTime time) {
        if (time == null || time.isBefore(startedAt)) {
            throw invalid("结果时间不能早于开始时间");
        }
        return time;
    }

    private static String period(String value) {
        String result = text(value, "targetAccountingPeriod");
        if (!result.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw invalid("目标会计期间必须为yyyy-MM");
        }
        return result;
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + "不能为空");
        }
        return value.trim();
    }

    private static String hash(String value, String field) {
        String result = text(value, field).toLowerCase(Locale.ROOT);
        if (!result.matches("[0-9a-f]{64}")) {
            throw invalid(field + "必须为SHA-256");
        }
        return result;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException invalid(String message) {
        return new MaintenanceValidationException(
                "MaintenanceRetroactivePeriodResolution", "resolution", message);
    }
}
