package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.common.enums.MaintenanceChangeType;
import com.titanium.maintenance.valueobject.MaintenanceId;

/**
 * 新增保全变更项命令（领域层）
 */
public record AddMaintenanceChangeCommand(@TargetAggregateIdentifier MaintenanceId id, MaintenanceChangeType changeType,
        String fieldName, String oldValue, String newValue, String createdBy) {
}
