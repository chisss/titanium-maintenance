package com.titanium.maintenance.application.command.casecreation;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;

/** 独立案件流程任务操作的应用层输入。 */
public record MaintenanceWorkflowTaskOperationInput(
        String maintenanceId,
        String taskId,
        String operationId,
        String evidenceVersion,
        String evidenceHash,
        String resultCode,
        String reason,
        MaintenanceWorkflowConditionDecision conditionDecision,
        String operatorId,
        String tenantId,
        MaintenanceChannel source) {
}
