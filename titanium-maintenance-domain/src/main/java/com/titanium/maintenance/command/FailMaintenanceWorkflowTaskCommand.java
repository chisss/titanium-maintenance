package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录保全流程任务失败。 */
public record FailMaintenanceWorkflowTaskCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        String failureCode,
        String failureReason,
        String operatorId) {
}
