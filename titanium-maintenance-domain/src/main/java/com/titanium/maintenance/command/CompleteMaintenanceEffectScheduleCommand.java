package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录未来生效计划已经取得 Policy 权威成功回执。 */
public record CompleteMaintenanceEffectScheduleCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String scheduleId,
        String attemptId,
        LocalDateTime completedAt,
        String operatorId) {
}
