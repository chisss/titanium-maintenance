package com.titanium.maintenance.application.command;

/** 撤销单个保全项目的应用层输入。 */
public record MaintenanceItemWithdrawalInput(
        String maintenanceId,
        String itemCode,
        String operationId,
        String reason,
        String paymentMethod,
        String operatorId,
        String tenantId) {
}
