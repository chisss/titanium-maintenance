package com.titanium.maintenance.event;

import java.time.OffsetDateTime;

import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;

/** 单个字段冲突已经显式解决，并生成新的案件级拟快照。 */
public record MaintenanceFieldConflictResolvedEvent(
        MaintenanceId maintenanceId,
        String operationId,
        String operationHash,
        MaintenanceFieldChange beforeChange,
        MaintenanceFieldChange afterChange,
        MaintenanceFieldConflictResolutionAction action,
        String reason,
        MaintenanceFieldConflictPlan plan,
        OffsetDateTime resolvedAt,
        String resolvedBy,
        String tenantId) {
}
