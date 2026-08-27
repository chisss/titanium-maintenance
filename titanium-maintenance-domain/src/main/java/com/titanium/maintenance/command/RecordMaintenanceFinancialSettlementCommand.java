package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录 Billing 返回的资金结算与佣金调整检查点。 */
public record RecordMaintenanceFinancialSettlementCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String postingId,
        String refundInstructionId,
        String refundOrderId,
        String refundStatus,
        Integer commissionAdjustmentCount,
        String updatedBy) {
}
