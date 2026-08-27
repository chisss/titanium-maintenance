package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录未来生效尝试失败，并决定退避重试或终止。 */
public record RecordMaintenanceEffectScheduleFailureCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String scheduleId,
        String attemptId,
        String errorCode,
        String errorMessage,
        LocalDateTime retryAt,
        boolean terminal,
        String operatorId) {
}
