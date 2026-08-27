package com.titanium.maintenance.query.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.maintenance.query.view.MaintenanceCaseItemView;

/** 独立保全案件项目投影仓储，所有读取均显式包含租户。 */
@Repository
public interface MaintenanceCaseItemViewRepository extends JpaRepository<MaintenanceCaseItemView, String> {

    Optional<MaintenanceCaseItemView> findByTenantIdAndMaintenanceIdAndItemCode(
            String tenantId, String maintenanceId, String itemCode);

    List<MaintenanceCaseItemView> findByTenantIdAndMaintenanceIdOrderByItemCodeAsc(
            String tenantId, String maintenanceId);

    List<MaintenanceCaseItemView> findByTenantIdAndMaintenanceIdInOrderByMaintenanceIdAscItemCodeAsc(
            String tenantId, Collection<String> maintenanceIds);
}
