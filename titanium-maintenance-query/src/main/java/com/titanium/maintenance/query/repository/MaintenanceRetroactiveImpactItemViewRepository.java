package com.titanium.maintenance.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.maintenance.query.view.MaintenanceRetroactiveImpactItemView;

/** 追溯影响项读模型仓储。 */
@Repository
public interface MaintenanceRetroactiveImpactItemViewRepository
        extends JpaRepository<MaintenanceRetroactiveImpactItemView, String> {

    List<MaintenanceRetroactiveImpactItemView> findByTenantIdAndMaintenanceIdAndAnalysisId(
            String tenantId, String maintenanceId, String analysisId);

    void deleteByTenantIdAndMaintenanceIdAndAnalysisId(
            String tenantId, String maintenanceId, String analysisId);
}
