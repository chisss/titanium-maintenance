package com.titanium.maintenance.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyApplicationEvidence;

/** 以案件为单位原子记录 Policy 权威回执并完成全部生效任务。 */
public record RecordMaintenanceCasePolicyApplicationCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        List<String> taskIds,
        String operationId,
        MaintenancePolicyApplicationEvidence evidence,
        String operatorId) {

    public RecordMaintenanceCasePolicyApplicationCommand {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }
}
