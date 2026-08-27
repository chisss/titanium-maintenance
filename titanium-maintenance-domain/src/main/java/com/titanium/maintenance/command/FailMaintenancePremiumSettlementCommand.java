package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 外部 Billing 或 Payment 调用失败时将费用任务置为可恢复失败。 */
public record FailMaintenancePremiumSettlementCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        String failureCode,
        String failureReason,
        String operatorId) {
}
