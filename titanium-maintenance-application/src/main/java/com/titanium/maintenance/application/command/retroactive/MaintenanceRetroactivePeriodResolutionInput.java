package com.titanium.maintenance.application.command.retroactive;

/** 人工后台与系统 API 共用的关闭会计期间处理输入。 */
public record MaintenanceRetroactivePeriodResolutionInput(
        String maintenanceId,
        String operationId,
        String targetAccountingPeriod,
        String reason,
        String operatorId,
        String tenantId) {
}
