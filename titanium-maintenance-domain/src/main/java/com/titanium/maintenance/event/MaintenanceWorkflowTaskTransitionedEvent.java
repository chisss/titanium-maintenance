package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;

/** 流程任务状态或领取信息已变更，并完整保留前后任务事实。 */
public record MaintenanceWorkflowTaskTransitionedEvent(
        MaintenanceId maintenanceId,
        MaintenanceWorkflowTask beforeTask,
        MaintenanceWorkflowTask afterTask,
        MaintenanceWorkflowTask activatedTaskBefore,
        MaintenanceWorkflowTask activatedTaskAfter,
        String operationId,
        String operationHash,
        LocalDateTime transitionedAt,
        String operatedBy,
        String tenantId) {
}
