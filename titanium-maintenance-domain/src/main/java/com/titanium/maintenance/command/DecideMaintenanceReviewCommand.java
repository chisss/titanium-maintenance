package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowReviewEvidence;

/** 使用已解析审核证据决定保全审核任务。 */
public record DecideMaintenanceReviewCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        MaintenanceWorkflowReviewEvidence evidence,
        String operatorId) {
}
