package com.titanium.maintenance.repository;

import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * 保全类型互斥配置仓储（driven port）
 * <p>
 * 保全互斥规则为本域参考/配置数据（非事件溯源聚合写状态），以 JPA CRUD 承载，表 {@code maintenance_exclusion}。
 * 领域层定义端口，基础设施层适配 JPA 实现。写侧收敛为纯事件溯源后，聚合状态不再经 JPA 持久化，
 * 本端口只保留互斥配置查询这一「参考数据取数」职责。
 * </p>
 */
public interface MaintenanceExclusionRepository {

    /**
     * 判断两种保全类型是否互斥
     *
     * @param existingType 已存在的保全类型
     * @param newType 新的保全类型
     * @param tenantId 租户ID
     * @return 是否互斥
     */
    boolean isMaintenanceTypeExcluded(MaintenanceType existingType, MaintenanceType newType, String tenantId);
}
