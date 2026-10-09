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

    /** 按案件批量取字段差异行（跨案历史：一次查询取回多个案件的行，避免逐案 N+1） */
    List<MaintenanceFieldChangeView> findByTenantIdAndMaintenanceIdInOrderByMaintenanceIdAscItemCodeAscFieldCodeAscObjectIdAsc(
            String tenantId, List<String> maintenanceIds);
}
