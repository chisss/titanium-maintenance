package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveBillingAdjustmentEvidence;

/** 记录 Billing 期间调整检查点并完成本次重算。 */
public record CompleteMaintenanceRetroactivePeriodRecalculationCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String periodRecalculationId,
        String operationId,
        MaintenanceRetroactiveBillingAdjustmentEvidence evidence,
        LocalDateTime completedAt,
        String operatorId) {
}
