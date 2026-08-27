package com.titanium.maintenance.application.command;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

/** 触发或刷新保全核保任务的应用输入。 */
public record MaintenanceUnderwritingAssessmentInput(
        String maintenanceId,
        String taskId,
        String operationId,
        String operatorId,
        String tenantId,
        MaintenanceChannel source) {
}
