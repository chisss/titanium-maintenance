package com.titanium.maintenance.query.query;

/**
 * 根据客户ID查询保全案件列表（CQRS 读侧查询入参）
 *
 * @param customerId 客户ID
 * @param tenantId 租户ID
 */
public record FindMaintenancesByCustomerIdQuery(String customerId, String tenantId) {
}
