package com.titanium.maintenance.service;

import java.util.ArrayList;
import java.util.List;

import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;

/** 将案件冻结的多项目步骤定义规划为稳定流程任务，不依赖任何外部 Port。 */
public class MaintenanceWorkflowPlanner {

    public List<MaintenanceWorkflowTask> plan(
            MaintenanceId maintenanceId,
            List<MaintenanceItemInstance> items) {
        if (maintenanceId == null || items == null || items.isEmpty()) {
            throw new MaintenanceValidationException(
                    "MaintenanceWorkflowPlanner", "items", "案件和冻结保全项不能为空");
        }
        List<MaintenanceWorkflowTask> tasks = new ArrayList<>();
        for (int itemOrder = 0; itemOrder < items.size(); itemOrder++) {
            MaintenanceItemInstance item = items.get(itemOrder);
            if (item == null) {
                throw new MaintenanceValidationException(
                        "MaintenanceWorkflowPlanner", "items", "冻结保全项不能包含空项");
            }
            appendItemTasks(tasks, maintenanceId, item, itemOrder);
        }
        return List.copyOf(tasks);
    }

    private void appendItemTasks(
            List<MaintenanceWorkflowTask> tasks,
            MaintenanceId maintenanceId,
            MaintenanceItemInstance item,
            int itemOrder) {
        boolean firstExecutableActivated = false;
        for (MaintenanceStepDefinition step : item.steps()) {
            MaintenanceWorkflowTaskStatus status;
            if (step.mode() == MaintenanceStepMode.SKIPPED) {
                status = MaintenanceWorkflowTaskStatus.SKIPPED;
            } else if (step.stepType() == MaintenanceStepType.CREATE) {
                status = MaintenanceWorkflowTaskStatus.COMPLETED;
            } else if (!firstExecutableActivated) {
                status = step.mode() == MaintenanceStepMode.CONDITIONAL
                        ? MaintenanceWorkflowTaskStatus.WAITING_CONDITION
                        : MaintenanceWorkflowTaskStatus.READY;
                firstExecutableActivated = true;
            } else {
                status = MaintenanceWorkflowTaskStatus.PENDING;
            }
            tasks.add(new MaintenanceWorkflowTask(
                    taskId(maintenanceId, item.itemCode(), step.stepType()),
                    item.itemCode(), itemOrder, step.sequence(), step.stepType(), step.mode(),
                    step.conditionRuleCode(), status));
        }
    }

    private String taskId(
            MaintenanceId maintenanceId,
            String itemCode,
            MaintenanceStepType stepType) {
        return maintenanceId.id() + ":" + itemCode + ":" + stepType.getCode();
    }
}
