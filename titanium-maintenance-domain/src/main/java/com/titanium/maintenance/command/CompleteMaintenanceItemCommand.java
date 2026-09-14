package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 在项目全部前置任务形成终态后完成案件终结标记步骤。 */
public record CompleteMaintenanceItemCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        String operatorId,
        String tenantId) {
}
