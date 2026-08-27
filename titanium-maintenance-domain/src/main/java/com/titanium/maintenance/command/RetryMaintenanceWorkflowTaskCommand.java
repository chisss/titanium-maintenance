package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 将失败任务恢复为可领取状态。 */
public record RetryMaintenanceWorkflowTaskCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        String reason,
        String operatorId) {
}
