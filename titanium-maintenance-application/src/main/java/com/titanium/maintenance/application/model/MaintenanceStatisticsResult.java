package com.titanium.maintenance.application.model;

/**
 * 保全统计结果（管理后台看板聚合）
 * <p>
 * application 读用例出参：由应用服务基于 CQRS 读模型 {@code MaintenanceView} 聚合统计，供表现层直接返回。
 * <b>非对外远程契约</b>，故不带 DTO 后缀、不置于 api 层。所有统计口径均按租户隔离。
 * </p>
 *
 * @param processingMaintenanceCount 处理中保全工单数（状态为待处理 PENDING 或处理中 PROCESSING）
 * @param todayMaintenanceCount      今日新增保全数（创建时间落在今日）
 * @param totalMaintenanceCount      保全总数
 */
public record MaintenanceStatisticsResult(long processingMaintenanceCount,
                                          long todayMaintenanceCount,
                                          long totalMaintenanceCount) {
}
