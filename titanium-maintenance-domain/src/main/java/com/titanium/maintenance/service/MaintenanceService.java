package com.titanium.maintenance.service;

import com.titanium.maintenance.aggregate.Maintenance;
import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;

import java.util.List;

public interface MaintenanceService {
    Maintenance findMaintenanceById(MaintenanceId id);
    List<Maintenance> findMaintenancesByPolicyId(PolicyId policyId);
    List<Maintenance> findPendingMaintenancesByPolicyId(PolicyId policyId);
    List<Maintenance> findMaintenancesByCustomerId(String customerId);
    boolean isPolicyUnderMaintenance(PolicyId policyId);
    Maintenance saveMaintenance(Maintenance maintenance);
    void deleteMaintenance(Maintenance maintenance);
    boolean isMaintenanceTypeExcluded(MaintenanceType existingType, MaintenanceType newType, String tenantId);
}