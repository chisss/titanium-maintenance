package com.titanium.maintenance.application.command;

/** 人工后台与系统 API 共用的追溯影响分析输入。 */
public record MaintenanceRetroactiveImpactAnalysisInput(
        String maintenanceId,
        String operationId,
        String operatorId,
        String tenantId) {
}
