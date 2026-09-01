package com.titanium.maintenance.application.command.effect;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

/** 人工/API 立即触发 Policy 合同应用的应用层输入。 */
public record MaintenanceEffectApplicationInput(
        String maintenanceId,
        String taskId,
        String operationId,
        String operatorId,
        String tenantId,
        MaintenanceChannel source) {
}
