package com.titanium.maintenance.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;

/** 以案件为单位原子冻结全部 Policy 生效任务请求。 */
public record RequestMaintenanceCaseEffectCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        List<String> taskIds,
        String operationId,
        MaintenanceEffectRequestEvidence evidence,
        String operatorId) {

    public RequestMaintenanceCaseEffectCommand {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }
}
