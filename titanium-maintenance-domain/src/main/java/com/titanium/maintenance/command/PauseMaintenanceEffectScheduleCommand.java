package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 暂停未来生效计划。 */
public record PauseMaintenanceEffectScheduleCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String scheduleId,
        String reason,
        String operatorId) {
}
