package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 在外部补偿前冻结项目撤销自动恢复所需的支付渠道。 */
public record ConfigureMaintenanceItemWithdrawalRecoveryCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String itemCode,
        String operationId,
        String requestHash,
        String paymentMethod,
        String configuredBy,
        String tenantId) {
}
