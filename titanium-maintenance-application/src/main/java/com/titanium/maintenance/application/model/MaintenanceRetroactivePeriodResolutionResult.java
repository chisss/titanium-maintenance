package com.titanium.maintenance.application.model;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;

/** 关闭会计期间处理结果，仅返回后台操作所需摘要。 */
public record MaintenanceRetroactivePeriodResolutionResult(
        String periodResolutionId,
        String operationId,
        MaintenanceRetroactivePeriodResolutionStatus status,
        String billingResolutionId,
        String billingBatchId,
        String sourceBatchResultHash,
        String targetAccountingPeriod,
        int resolvedLineCount,
        String resultHash,
        String reason,
        String failureCode,
        String failureMessage,
        LocalDateTime completedAt) {
}
