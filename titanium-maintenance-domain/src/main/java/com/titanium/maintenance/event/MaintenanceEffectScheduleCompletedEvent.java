package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 未来生效计划已完成并取得 Policy 权威成功事实。 */
public record MaintenanceEffectScheduleCompletedEvent(
        MaintenanceId maintenanceId,
        String scheduleId,
        String attemptId,
        LocalDateTime completedAt,
        String completedBy,
        String tenantId) {
}
