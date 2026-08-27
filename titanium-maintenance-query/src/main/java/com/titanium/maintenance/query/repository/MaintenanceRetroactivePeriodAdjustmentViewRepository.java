package com.titanium.maintenance.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;

/** 追溯期间调整读模型仓储。 */
@Repository
public interface MaintenanceRetroactivePeriodAdjustmentViewRepository
        extends JpaRepository<MaintenanceRetroactivePeriodAdjustmentView, String> {

    List<MaintenanceRetroactivePeriodAdjustmentView>
            findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                    String tenantId, String maintenanceId, String periodRecalculationId);

    void deleteByTenantIdAndMaintenanceId(String tenantId, String maintenanceId);
}
