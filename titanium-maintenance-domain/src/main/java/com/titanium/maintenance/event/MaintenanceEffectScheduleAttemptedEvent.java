package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 调度节点已持有租约并开始一次执行尝试。 */
public record MaintenanceEffectScheduleAttemptedEvent(
        MaintenanceId maintenanceId,
        String scheduleId,
        String attemptId,
        int attemptNumber,
        LocalDateTime attemptedAt,
        String attemptedBy,
        String tenantId) {
}
