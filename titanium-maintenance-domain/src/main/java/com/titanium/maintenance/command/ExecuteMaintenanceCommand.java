package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/**
 * 执行保全命令（领域层）
 */
public record ExecuteMaintenanceCommand(@TargetAggregateIdentifier MaintenanceId id, LocalDateTime effectiveTime,
        String executionDetails, String updatedBy) {
}
