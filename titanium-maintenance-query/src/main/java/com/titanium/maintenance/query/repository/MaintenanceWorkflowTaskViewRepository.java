package com.titanium.maintenance.query.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;

/** 保全流程任务投影仓储，读取必须显式包含租户。 */
@Repository
public interface MaintenanceWorkflowTaskViewRepository
        extends JpaRepository<MaintenanceWorkflowTaskView, String> {

    List<MaintenanceWorkflowTaskView> findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
            String tenantId, String maintenanceId);

    Optional<MaintenanceWorkflowTaskView> findByTenantIdAndMaintenanceIdAndTaskId(
            String tenantId, String maintenanceId, String taskId);
}
