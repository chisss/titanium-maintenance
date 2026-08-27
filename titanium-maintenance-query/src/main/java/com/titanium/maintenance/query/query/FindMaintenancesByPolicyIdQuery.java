package com.titanium.maintenance.query.query;

/**
 * 根据保单ID查询保全案件列表（CQRS 读侧查询入参）
 *
 * @param policyId 保单ID
 * @param tenantId 租户ID
 */
public record FindMaintenancesByPolicyIdQuery(String policyId, String tenantId) {
}
