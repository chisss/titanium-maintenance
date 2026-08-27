package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 案件未来生效计划已恢复。 */
public record MaintenanceEffectScheduleResumedEvent(
        MaintenanceId maintenanceId,
        String scheduleId,
        LocalDateTime nextExecutionAt,
        String reason,
        LocalDateTime resumedAt,
        String resumedBy,
        String tenantId) {
}
