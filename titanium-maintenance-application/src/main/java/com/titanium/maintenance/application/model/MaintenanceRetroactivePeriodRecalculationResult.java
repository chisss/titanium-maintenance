package com.titanium.maintenance.application.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;

/** 追溯期间重算触发结果，仅返回后台操作所需摘要。 */
public record MaintenanceRetroactivePeriodRecalculationResult(
        String periodRecalculationId,
        int periodRecalculationVersion,
        String operationId,
        MaintenanceRetroactivePeriodRecalculationStatus status,
        String analysisId,
        int analysisVersion,
        String analysisResultHash,
        String productRecalculationId,
        MaintenanceBalanceDirection direction,
        BigDecimal amount,
        String currency,
        int periodCount,
        String billingBatchId,
        String billingStatus,
        int postedCount,
        int reviewCount,
        String failureCode,
        String failureMessage,
        LocalDateTime completedAt) {
}
