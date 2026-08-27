package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawalCompensation;

/** 记录项目撤销的 Billing 冲正与 Payment 资金结果。 */
public record RecordMaintenanceItemWithdrawalCompensationCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String itemCode,
        String operationId,
        String requestHash,
        MaintenanceItemWithdrawalCompensation compensation,
        String operatorId) {
}
