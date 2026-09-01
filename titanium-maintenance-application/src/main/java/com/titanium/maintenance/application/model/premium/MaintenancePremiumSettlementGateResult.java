package com.titanium.maintenance.application.model.premium;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;

/** 费用任务当前 Billing 入账与 Payment 资金门禁结果。 */
public record MaintenancePremiumSettlementGateResult(
        MaintenanceWorkflowTaskStatus taskStatus,
        String postingId,
        MaintenanceBillingPostingStatus postingStatus,
        MaintenanceBalanceDirection direction,
        BigDecimal amount,
        String currency,
        MaintenanceFundSettlementType fundType,
        MaintenanceFundSettlementStatus fundStatus,
        String instructionId,
        String orderId,
        String externalStatus,
        String failureCode,
        String failureMessage,
        LocalDateTime recordedAt) {
}
