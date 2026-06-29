package com.titanium.maintenance.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.maintenance.enums.EffectiveTimeStatus;
import com.titanium.maintenance.repository.MaintenanceEffectiveTimeJpaEntity;

public interface MaintenanceEffectiveTimeJpaRepository extends JpaRepository<MaintenanceEffectiveTimeJpaEntity, String> {

    List<MaintenanceEffectiveTimeJpaEntity> findByMaintenanceCaseId(String maintenanceCaseId);

    List<MaintenanceEffectiveTimeJpaEntity> findByStatus(EffectiveTimeStatus status);
}
