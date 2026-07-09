package com.titanium.maintenance.infrastructure.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.maintenance.common.enums.EffectiveTimeStatus;
import com.titanium.maintenance.infrastructure.entity.MaintenanceEffectiveTimeEntity;

public interface MaintenanceEffectiveTimeJpaRepository extends JpaRepository<MaintenanceEffectiveTimeEntity, String> {

    List<MaintenanceEffectiveTimeEntity> findByMaintenanceCaseId(String maintenanceCaseId);

    List<MaintenanceEffectiveTimeEntity> findByStatus(EffectiveTimeStatus status);
}
