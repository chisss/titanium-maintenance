package com.titanium.maintenance.repository.jpa;

import com.titanium.maintenance.enums.EffectiveTimeStatus;
import com.titanium.maintenance.repository.MaintenanceEffectiveTimeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceEffectiveTimeJpaRepository extends JpaRepository<MaintenanceEffectiveTimeJpaEntity, String> {

    List<MaintenanceEffectiveTimeJpaEntity> findByMaintenanceCaseId(String maintenanceCaseId);

    List<MaintenanceEffectiveTimeJpaEntity> findByStatus(EffectiveTimeStatus status);
}