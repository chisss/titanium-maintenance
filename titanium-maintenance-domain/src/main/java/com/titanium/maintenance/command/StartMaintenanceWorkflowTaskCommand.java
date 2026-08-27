package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 开始处理已领取的保全流程任务。 */
public record StartMaintenanceWorkflowTaskCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        String operatorId) {
}
