package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 领取保全流程任务。 */
public record ClaimMaintenanceWorkflowTaskCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        String operatorId) {
}
