package com.titanium.maintenance.application.model.effect;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;

/** 后台可直接展示的未来生效计划状态。 */
public record MaintenanceEffectScheduleResult(
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
