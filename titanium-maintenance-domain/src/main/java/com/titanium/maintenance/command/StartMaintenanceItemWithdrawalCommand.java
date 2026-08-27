package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 冻结单个保全项目撤销请求，外部财务调用只能在该事实之后执行。 */
public record StartMaintenanceItemWithdrawalCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String itemCode,
        String operationId,
        String requestHash,
        String reason,
        String operatorId,
        String tenantId) {
}
