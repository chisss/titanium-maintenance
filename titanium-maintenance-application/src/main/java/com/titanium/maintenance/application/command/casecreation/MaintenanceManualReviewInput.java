package com.titanium.maintenance.application.command.casecreation;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;

/** 人工审核任务的应用层输入。 */
public record MaintenanceManualReviewInput(
        String maintenanceId,
        String taskId,
        String operationId,
        MaintenanceReviewDecision decision,
        String policyVersion,
        String comment,
        String operatorId,
        String tenantId,
        MaintenanceChannel source) {
}
