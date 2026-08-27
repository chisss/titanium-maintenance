package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;

/** 冻结 Policy 应用请求并将生效任务置为等待外部结果。 */
public record RequestMaintenanceEffectCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        MaintenanceEffectRequestEvidence evidence,
        String operatorId) {
}
