package com.titanium.maintenance.infrastructure.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.infrastructure.entity.MaintenanceCaseEntity;

public interface MaintenanceCaseJpaRepository extends JpaRepository<MaintenanceCaseEntity, String> {

    List<MaintenanceCaseEntity> findByPolicyId(String policyId);

    List<MaintenanceCaseEntity> findByPolicyIdAndStatusIn(String policyId, List<MaintenanceStatus> statuses);

    @Query("SELECT m FROM MaintenanceCaseEntity m WHERE m.policyId = :policyId AND m.status IN ('PENDING', 'PROCESSING', 'APPROVED')")
    List<MaintenanceCaseEntity> findPendingMaintenanceByPolicyId(@Param("policyId") String policyId);

    List<MaintenanceCaseEntity> findByCustomerId(String customerId);

    List<MaintenanceCaseEntity> findByStatus(MaintenanceStatus status);
}
