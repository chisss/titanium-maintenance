package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录 Billing 已登记的保全余额事实检查点。 */
public record RecordMaintenancePremiumPostingCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String adjustmentId,
        String adjustmentResultHash,
        String postingId,
        String postingStatus,
        String updatedBy) {
}
