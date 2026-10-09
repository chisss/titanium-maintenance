package com.titanium.maintenance.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.maintenance.query.view.MaintenanceSurrenderFactorView;

/** 退保退费因子明细读模型仓储。 */
@Repository
public interface MaintenanceSurrenderFactorViewRepository
        extends JpaRepository<MaintenanceSurrenderFactorView, String> {

    /** 按案件清空因子明细（重投影幂等：先删后插，见投影处理器） */
    void deleteByTenantIdAndMaintenanceId(String tenantId, String maintenanceId);

    /** 按案件取因子明细（保持对端下发顺序） */
    List<MaintenanceSurrenderFactorView> findByTenantIdAndMaintenanceIdOrderByFactorOrderAsc(
            String tenantId, String maintenanceId);
}
