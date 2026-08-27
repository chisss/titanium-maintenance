package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录租约节点已开始一次未来生效尝试。 */
public record RecordMaintenanceEffectScheduleAttemptCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String scheduleId,
        String attemptId,
        LocalDateTime attemptedAt,
        String operatorId) {
}
