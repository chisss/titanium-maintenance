package com.titanium.maintenance.query.service;

import java.util.List;
import java.util.Optional;

import com.titanium.maintenance.query.result.MaintenanceQueryResult;

/**
 * 保全查询服务（CQRS 读侧）
 * <p>
 * 查询由事件投影维护的读模型表 {@code t_maintenance_view}，返回稳定 DTO 契约。
 * </p>
 */
public interface MaintenanceQueryService {

    /**
     * 根据保全案件ID查询摘要
     */
    Optional<MaintenanceQueryResult> getMaintenanceSummary(String maintenanceId);

    /**
     * 根据保单ID查询保全案件摘要列表
     */
    List<MaintenanceQueryResult> getMaintenanceSummariesByPolicyId(String policyId);

    /**
     * 根据客户ID查询保全案件摘要列表
     */
    List<MaintenanceQueryResult> getMaintenanceSummariesByCustomerId(String customerId);
}
