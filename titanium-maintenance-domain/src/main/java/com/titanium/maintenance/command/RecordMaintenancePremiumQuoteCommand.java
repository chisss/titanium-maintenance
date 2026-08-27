package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenancePremiumQuoteEvidence;

/** 将 Product 权威报价或无需报价结论写入费用任务。 */
public record RecordMaintenancePremiumQuoteCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        MaintenancePremiumQuoteEvidence evidence,
        String operatorId) {
}
