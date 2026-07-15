package com.titanium.maintenance.infrastructure.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.titanium.maintenance.infrastructure.entity.MaintenanceExclusionDO;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * 保全类型互斥配置 JPA 仓储（参考数据 CRUD，非写侧聚合）。
 */
public interface MaintenanceExclusionJpaRepository extends JpaRepository<MaintenanceExclusionDO, String> {

    @Query("SELECT m FROM MaintenanceExclusionDO m WHERE (m.maintenanceType1 = :maintenanceType OR m.maintenanceType2 = :maintenanceType) AND m.tenantId = :tenantId")
    List<MaintenanceExclusionDO> findExclusionsByMaintenanceType(@Param("maintenanceType") MaintenanceType maintenanceType, @Param("tenantId") String tenantId);

    List<MaintenanceExclusionDO> findByTenantId(String tenantId);
}
