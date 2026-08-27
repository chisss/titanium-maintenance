package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 一次未来生效执行失败，事件明确是否继续退避重试。 */
public record MaintenanceEffectScheduleFailedEvent(
        MaintenanceId maintenanceId,
        String scheduleId,
        String attemptId,
        String errorCode,
        String errorMessage,
        LocalDateTime retryAt,
        boolean terminal,
        LocalDateTime failedAt,
        String failedBy,
        String tenantId) {
}
