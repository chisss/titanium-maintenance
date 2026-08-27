package com.titanium.maintenance.web.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;

/** 追溯期间重算操作摘要；逐期间明细通过案件详情返回。 */
public record MaintenanceRetroactivePeriodRecalculationVO(
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
