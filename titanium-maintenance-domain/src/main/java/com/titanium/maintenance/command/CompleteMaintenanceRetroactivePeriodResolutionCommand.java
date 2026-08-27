package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolutionEvidence;

/** 保存 Billing 关闭期间处理结论。 */
public record CompleteMaintenanceRetroactivePeriodResolutionCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String periodResolutionId,
        String operationId,
        MaintenanceRetroactivePeriodResolutionEvidence evidence,
        LocalDateTime completedAt,
        String operatorId) {
}
