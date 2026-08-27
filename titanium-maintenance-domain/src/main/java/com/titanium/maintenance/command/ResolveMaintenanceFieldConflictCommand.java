package com.titanium.maintenance.command;

import java.time.OffsetDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;

/** 对单个顺序外字段冲突记录显式解决结论。 */
public record ResolveMaintenanceFieldConflictCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String operationId,
        String requestHash,
        String itemCode,
        String objectId,
        String fieldCode,
        MaintenanceFieldConflictResolutionAction action,
        MaintenanceFieldValue reenteredValue,
        String reason,
        OffsetDateTime resolvedAt,
        String resolvedBy,
        String tenantId) {
}
