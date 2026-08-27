package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 在未取得可记录的外部补偿事实时关闭本次撤销尝试。 */
public record FailMaintenanceItemWithdrawalCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String itemCode,
        String operationId,
        String requestHash,
        String failureCode,
        String failureMessage,
        String operatorId) {
}
