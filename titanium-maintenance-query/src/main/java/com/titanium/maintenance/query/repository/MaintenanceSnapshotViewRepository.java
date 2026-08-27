package com.titanium.maintenance.query.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.maintenance.query.view.MaintenanceSnapshotView;

/** 独立保全案件快照引用投影仓储。 */
@Repository
public interface MaintenanceSnapshotViewRepository extends JpaRepository<MaintenanceSnapshotView, String> {

    Optional<MaintenanceSnapshotView> findByMaintenanceIdAndTenantId(
            String maintenanceId, String tenantId);
}
