package com.titanium.maintenance.web.response;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;

/** 关闭会计期间处理摘要。 */
public record MaintenanceRetroactivePeriodResolutionVO(
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
