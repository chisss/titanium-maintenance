package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.MaintenanceId;

/** 为已完成项目冻结的案件显式初始化流程任务，可用于历史案件幂等回填。 */
public record InitializeMaintenanceWorkflowCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String initializedBy) {

    public InitializeMaintenanceWorkflowCommand {
        if (id == null || initializedBy == null || initializedBy.isBlank()) {
            throw new MaintenanceValidationException(
                    "InitializeMaintenanceWorkflowCommand", "工作流初始化参数不完整");
        }
        initializedBy = initializedBy.trim();
    }
}
