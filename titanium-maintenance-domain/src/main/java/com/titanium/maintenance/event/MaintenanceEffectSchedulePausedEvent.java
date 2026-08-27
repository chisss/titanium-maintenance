package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 案件未来生效计划已暂停。 */
public record MaintenanceEffectSchedulePausedEvent(
        MaintenanceId maintenanceId,
        String scheduleId,
        String reason,
        LocalDateTime pausedAt,
        String pausedBy,
        String tenantId) {
}
