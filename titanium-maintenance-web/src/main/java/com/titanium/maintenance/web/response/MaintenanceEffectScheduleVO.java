package com.titanium.maintenance.web.response;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;

/** 后台未来生效计划响应。 */
public record MaintenanceEffectScheduleVO(
        String scheduleId,
        EffectiveTimeType effectiveTimeType,
        MaintenanceEffectScheduleStatus status,
        String tenantZoneId,
        LocalDateTime nextExecutionAt,
        int attemptCount,
        String lastAttemptId,
        LocalDateTime lastAttemptAt,
        String lastErrorCode,
        String lastErrorMessage) {
}
