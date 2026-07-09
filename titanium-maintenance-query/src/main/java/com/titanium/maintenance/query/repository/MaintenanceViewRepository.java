package com.titanium.maintenance.query.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.query.view.MaintenanceView;

/**
 * 保全案件读模型仓储
 * <p>
 * CQRS 查询侧仓储，访问读模型表 {@code t_maintenance_view}。租户隔离经 {@code tenantId} 条件下推。
 * </p>
 */
@Repository
public interface MaintenanceViewRepository
        extends JpaRepository<MaintenanceView, String>, JpaSpecificationExecutor<MaintenanceView> {

    Optional<MaintenanceView> findByMaintenanceId(String maintenanceId);

    Optional<MaintenanceView> findByMaintenanceIdAndTenantId(String maintenanceId, String tenantId);

    List<MaintenanceView> findByPolicyId(String policyId);

    List<MaintenanceView> findByCustomerId(String customerId);

    List<MaintenanceView> findByStatus(MaintenanceStatus status);

    List<MaintenanceView> findByPolicyIdAndStatusIn(String policyId, List<MaintenanceStatus> statuses);
}
