package com.titanium.maintenance.query.query;

/**
 * 根据ID查询保全案件（CQRS 读侧查询入参）
 *
 * @param maintenanceId 保全案件ID
 * @param tenantId 租户ID
 */
public record FindMaintenanceByIdQuery(String maintenanceId, String tenantId) {
}
