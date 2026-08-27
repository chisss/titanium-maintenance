package com.titanium.maintenance.application.command;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

/** 未来生效计划人工/API 操作上下文。 */
public record MaintenanceEffectScheduleOperationInput(
        String maintenanceId,
        String operationId,
        String reason,
        String operatorId,
        String tenantId,
        MaintenanceChannel source) {
}
