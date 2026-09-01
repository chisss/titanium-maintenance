package com.titanium.maintenance.web.response.premium;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;

/** 费用任务 Billing 与 Payment 门禁响应。 */
public record MaintenancePremiumSettlementGateVO(
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
