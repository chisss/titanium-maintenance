package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 完成信息录入或业务校验任务。 */
public record CompleteMaintenanceWorkflowTaskCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        String evidenceVersion,
        String evidenceHash,
        String resultCode,
        String reason,
        String operatorId) {
}
