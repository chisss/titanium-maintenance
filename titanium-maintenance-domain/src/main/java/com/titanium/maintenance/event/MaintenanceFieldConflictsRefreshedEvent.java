package com.titanium.maintenance.event;

import java.time.OffsetDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;

/** 案件字段已按 Policy 最新版本刷新并重建拟快照。 */
public record MaintenanceFieldConflictsRefreshedEvent(
        MaintenanceId maintenanceId,
        String operationId,
        String operationHash,
        MaintenanceFieldConflictPlan plan,
        OffsetDateTime refreshedAt,
        String refreshedBy,
        String tenantId) {
}
