package com.titanium.maintenance.valueobject.workflow;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 流程任务领取信息。 */
public record MaintenanceWorkflowAssignment(String assignee, LocalDateTime claimedAt) {

    public MaintenanceWorkflowAssignment {
        if (assignee == null || assignee.isBlank() || claimedAt == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowAssignment", "领取人和领取时间不能为空");
        }
        assignee = assignee.trim();
    }
}
