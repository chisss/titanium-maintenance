package com.titanium.maintenance.repository;

import java.util.List;
import java.util.Optional;

import com.titanium.maintenance.aggregate.Maintenance;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

public interface MaintenanceRepository {
    Optional<Maintenance> findById(MaintenanceId id);

    List<Maintenance> findByPolicyId(PolicyId policyId);

    List<Maintenance> findByCustomerId(String customerId);

    Maintenance save(Maintenance maintenance);

    void delete(Maintenance maintenance);

    /**
     * 判断两种保全类型是否互斥（领域抽象，实现在基础设施层查询互斥配置表）
     *
     * @param existingType 已存在的保全类型
     * @param newType 新的保全类型
     * @param tenantId 租户ID
     * @return 是否互斥
     */
    boolean isMaintenanceTypeExcluded(MaintenanceType existingType, MaintenanceType newType, String tenantId);
}
