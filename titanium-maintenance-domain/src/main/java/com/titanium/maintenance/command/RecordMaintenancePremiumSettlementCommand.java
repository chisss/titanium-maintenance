package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceBillingPostingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceFundSettlementEvidence;

/** 将 Billing 入账与独立资金结果写入费用任务。 */
public record RecordMaintenancePremiumSettlementCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        MaintenanceBillingPostingEvidence postingEvidence,
        MaintenanceFundSettlementEvidence fundEvidence,
        String operatorId) {
}
