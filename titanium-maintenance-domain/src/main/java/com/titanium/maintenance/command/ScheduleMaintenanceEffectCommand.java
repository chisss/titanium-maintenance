package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 为未来生效案件创建稳定计划。 */
public record ScheduleMaintenanceEffectCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String scheduleId,
        String tenantZoneId,
        LocalDateTime nextExecutionAt,
        String operatorId) {
}
