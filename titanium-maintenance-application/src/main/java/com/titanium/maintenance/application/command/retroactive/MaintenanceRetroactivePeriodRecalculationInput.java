package com.titanium.maintenance.application.command.retroactive;

/** 人工后台与系统 API 共用的追溯期间重算输入。 */
public record MaintenanceRetroactivePeriodRecalculationInput(
        String maintenanceId,
        String operationId,
        String operatorId,
        String tenantId) {
}
