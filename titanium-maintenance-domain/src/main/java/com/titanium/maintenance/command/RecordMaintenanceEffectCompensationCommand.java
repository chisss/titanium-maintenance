package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectCompensationEvidence;

/** 记录 Policy 成功后案件回执写入失败的独立补偿事实。 */
public record RecordMaintenanceEffectCompensationCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        MaintenanceEffectCompensationEvidence evidence,
        String operatorId) {
}
