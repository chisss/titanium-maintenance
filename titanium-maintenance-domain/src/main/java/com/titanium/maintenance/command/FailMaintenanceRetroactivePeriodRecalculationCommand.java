package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录 Product 或 Billing 期间重算失败事实。 */
public record FailMaintenanceRetroactivePeriodRecalculationCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String periodRecalculationId,
        String operationId,
        String failureCode,
        String failureMessage,
        LocalDateTime failedAt,
        String operatorId) {
}
