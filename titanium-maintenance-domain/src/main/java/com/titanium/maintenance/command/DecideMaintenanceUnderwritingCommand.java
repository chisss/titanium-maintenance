package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceUnderwritingEvidence;

/** 使用 Underwriting 权威证据决定保全核保任务。 */
public record DecideMaintenanceUnderwritingCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        MaintenanceUnderwritingEvidence evidence,
        String operatorId) {
}
