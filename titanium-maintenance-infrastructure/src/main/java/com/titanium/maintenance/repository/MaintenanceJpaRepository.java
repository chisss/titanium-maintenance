package com.titanium.maintenance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.titanium.maintenance.enums.MaintenanceStatus;

@Repository
public interface MaintenanceJpaRepository extends JpaRepository<MaintenanceJpaEntity, String> {
    List<MaintenanceJpaEntity> findByPolicyId(String policyId);
    List<MaintenanceJpaEntity> findByCustomerId(String customerId);
    List<MaintenanceJpaEntity> findByStatus(MaintenanceStatus status);
}
