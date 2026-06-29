package com.titanium.maintenance.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.repository.MaintenanceExclusionJpaEntity;

public interface MaintenanceExclusionJpaRepository extends JpaRepository<MaintenanceExclusionJpaEntity, String> {

    @Query("SELECT m FROM MaintenanceExclusionJpaEntity m WHERE (m.maintenanceType1 = :maintenanceType OR m.maintenanceType2 = :maintenanceType) AND m.tenantId = :tenantId")
    List<MaintenanceExclusionJpaEntity> findExclusionsByMaintenanceType(@Param("maintenanceType") MaintenanceType maintenanceType, @Param("tenantId") String tenantId);

    List<MaintenanceExclusionJpaEntity> findByTenantId(String tenantId);
}
