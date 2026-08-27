package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.valueobject.MaintenanceId;

/** 保全案件正交生效状态已变化。 */
public record MaintenanceEffectStatusChangedEvent(
        MaintenanceId maintenanceId,
        String taskId,
        MaintenanceEffectStatus previousStatus,
        MaintenanceEffectStatus currentStatus,
        String reason,
        LocalDateTime changedAt,
        String changedBy,
        String tenantId) {
}
