package com.titanium.maintenance.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;

/** 独立保全案件字段差异投影仓储。 */
@Repository
public interface MaintenanceFieldChangeViewRepository extends JpaRepository<MaintenanceFieldChangeView, String> {

    void deleteByTenantIdAndMaintenanceIdAndItemCode(
            String tenantId, String maintenanceId, String itemCode);

    List<MaintenanceFieldChangeView> findByTenantIdAndMaintenanceIdAndItemCodeOrderByFieldCodeAscObjectIdAsc(
            String tenantId, String maintenanceId, String itemCode);

    List<MaintenanceFieldChangeView> findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
            String tenantId, String maintenanceId);
}
