package com.titanium.maintenance.infrastructure.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.maintenance.common.enums.MaintenanceChangeType;
import com.titanium.maintenance.infrastructure.entity.MaintenanceChangeRecordEntity;

public interface MaintenanceChangeRecordJpaRepository extends JpaRepository<MaintenanceChangeRecordEntity, String> {

    List<MaintenanceChangeRecordEntity> findByMaintenanceCaseId(String maintenanceCaseId);

    List<MaintenanceChangeRecordEntity> findByMaintenanceCaseIdAndChangeType(String maintenanceCaseId, MaintenanceChangeType changeType);
}
