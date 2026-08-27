package com.titanium.maintenance.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 以案件为单位原子标记全部已发起生效任务失败。 */
public record FailMaintenanceCaseEffectCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        List<String> taskIds,
        String operationId,
        String failureCode,
        String failureReason,
        String operatorId) {

    public FailMaintenanceCaseEffectCommand {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }
}
