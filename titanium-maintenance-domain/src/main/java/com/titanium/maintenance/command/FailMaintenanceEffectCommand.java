package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** Policy 调用或回执校验失败时将生效任务置为可恢复失败。 */
public record FailMaintenanceEffectCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        String failureCode,
        String failureReason,
        String operatorId) {
}
