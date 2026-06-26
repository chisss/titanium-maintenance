package com.titanium.maintenance.repository.jpa;

import com.titanium.maintenance.enums.MaintenanceChangeType;
import com.titanium.maintenance.repository.MaintenanceChangeRecordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceChangeRecordJpaRepository extends JpaRepository<MaintenanceChangeRecordJpaEntity, String> {

    List<MaintenanceChangeRecordJpaEntity> findByMaintenanceCaseId(String maintenanceCaseId);

    List<MaintenanceChangeRecordJpaEntity> findByMaintenanceCaseIdAndChangeType(String maintenanceCaseId, MaintenanceChangeType changeType);
}