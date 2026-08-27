package com.titanium.maintenance.event;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;

/** 案件冻结步骤已经实例化为可审计流程任务。 */
public record MaintenanceWorkflowInitializedEvent(
        MaintenanceId maintenanceId,
        List<MaintenanceWorkflowTask> tasks,
        LocalDateTime initializedAt,
        String initializedBy,
        String tenantId) {

    public MaintenanceWorkflowInitializedEvent {
        tasks = List.copyOf(tasks);
    }
}
