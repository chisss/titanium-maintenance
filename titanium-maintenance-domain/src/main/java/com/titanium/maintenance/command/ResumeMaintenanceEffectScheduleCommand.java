package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 恢复暂停或失败的未来生效计划。 */
public record ResumeMaintenanceEffectScheduleCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String scheduleId,
        String operationId,
        LocalDateTime nextExecutionAt,
        String reason,
        String operatorId) {
}
