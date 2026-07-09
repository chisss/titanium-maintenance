package com.titanium.maintenance.infrastructure.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.titanium.maintenance.infrastructure.entity.MaintenanceExclusionEntity;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

public interface MaintenanceExclusionJpaRepository extends JpaRepository<MaintenanceExclusionEntity, String> {

    @Query("SELECT m FROM MaintenanceExclusionEntity m WHERE (m.maintenanceType1 = :maintenanceType OR m.maintenanceType2 = :maintenanceType) AND m.tenantId = :tenantId")
    List<MaintenanceExclusionEntity> findExclusionsByMaintenanceType(@Param("maintenanceType") MaintenanceType maintenanceType, @Param("tenantId") String tenantId);

    List<MaintenanceExclusionEntity> findByTenantId(String tenantId);
}
