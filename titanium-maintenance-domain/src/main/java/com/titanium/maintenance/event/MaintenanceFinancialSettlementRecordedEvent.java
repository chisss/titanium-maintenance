package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenancePremiumSettlementStatus;
import com.titanium.maintenance.valueobject.MaintenanceId;

/** Billing 资金结算与佣金调整检查点事件。 */
public record MaintenanceFinancialSettlementRecordedEvent(
        MaintenanceId maintenanceId,
        String postingId,
        String refundInstructionId,
        String refundOrderId,
        String refundStatus,
        Integer commissionAdjustmentCount,
        MaintenancePremiumSettlementStatus premiumSettlementStatus,
        LocalDateTime recordedAt,
        String updatedBy,
        String tenantId) {
}
