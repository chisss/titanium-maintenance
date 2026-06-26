package com.titanium.maintenance.repository.jpa;

import com.titanium.maintenance.enums.MaintenanceStatus;
import com.titanium.maintenance.repository.MaintenanceCaseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MaintenanceCaseJpaRepository extends JpaRepository<MaintenanceCaseJpaEntity, String> {

    List<MaintenanceCaseJpaEntity> findByPolicyId(String policyId);

    List<MaintenanceCaseJpaEntity> findByPolicyIdAndStatusIn(String policyId, List<MaintenanceStatus> statuses);

    @Query("SELECT m FROM MaintenanceCaseJpaEntity m WHERE m.policyId = :policyId AND m.status IN ('PENDING', 'PROCESSING', 'APPROVED')")
    List<MaintenanceCaseJpaEntity> findPendingMaintenanceByPolicyId(@Param("policyId") String policyId);

    List<MaintenanceCaseJpaEntity> findByCustomerId(String customerId);

    List<MaintenanceCaseJpaEntity> findByStatus(MaintenanceStatus status);
}