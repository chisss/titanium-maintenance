package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyApplicationEvidence;

/** 记录 Policy 权威应用回执并完成生效任务。 */
public record RecordMaintenancePolicyApplicationCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        MaintenancePolicyApplicationEvidence evidence,
        String operatorId) {
}
