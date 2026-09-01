package com.titanium.maintenance.application.model.withdrawal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalFundAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalStatus;

/** 项目撤销当前权威结果。 */
public record MaintenanceItemWithdrawalResult(
        String itemCode,
        String operationId,
        String requestHash,
        MaintenanceItemWithdrawalStatus status,
        String sourcePostingId,
        MaintenanceFundSettlementStatus sourceFundStatus,
        String reversalId,
        String reversalResultHash,
        MaintenanceBalanceDirection reversalDirection,
        BigDecimal amount,
        String currency,
        MaintenanceItemWithdrawalFundAction fundAction,
        MaintenanceFundSettlementStatus fundStatus,
        String fundRequestId,
        String fundOrderId,
        String fundExternalStatus,
        String failureCode,
        String failureMessage,
        int retryCount,
        LocalDateTime requestedAt,
        LocalDateTime completedAt) {
}
