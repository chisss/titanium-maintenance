package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录条件任务的权威规则结论。 */
public record DecideMaintenanceWorkflowConditionCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        String ruleVersion,
        String inputHash,
        MaintenanceWorkflowConditionDecision decision,
        String reason,
        String operatorId) {
}
