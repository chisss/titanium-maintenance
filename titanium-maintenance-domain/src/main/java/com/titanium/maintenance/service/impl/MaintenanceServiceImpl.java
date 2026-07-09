package com.titanium.maintenance.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.aggregate.Maintenance;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.exception.MaintenanceStatusException;
import com.titanium.maintenance.repository.MaintenanceRepository;
import com.titanium.maintenance.service.MaintenanceService;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

@Service
public class MaintenanceServiceImpl implements MaintenanceService {
    private final MaintenanceRepository maintenanceRepository;

    public MaintenanceServiceImpl(MaintenanceRepository maintenanceRepository) {
        this.maintenanceRepository = maintenanceRepository;
    }

    @Override
    public Maintenance findMaintenanceById(MaintenanceId id) {
        return maintenanceRepository.findById(id).orElseThrow(MaintenanceNotFoundException::new);
    }

    @Override
    public List<Maintenance> findMaintenancesByPolicyId(PolicyId policyId) {
        return maintenanceRepository.findByPolicyId(policyId);
    }

    @Override
    public List<Maintenance> findPendingMaintenancesByPolicyId(PolicyId policyId) {
        return maintenanceRepository
                .findByPolicyId(policyId).stream().filter(m -> m.getStatus() == MaintenanceStatus.PENDING
                        || m.getStatus() == MaintenanceStatus.PROCESSING || m.getStatus() == MaintenanceStatus.APPROVED)
                .collect(Collectors.toList());
    }

    @Override
    public List<Maintenance> findMaintenancesByCustomerId(String customerId) {
        return maintenanceRepository.findByCustomerId(customerId);
    }

    @Override
    public boolean isPolicyUnderMaintenance(PolicyId policyId) {
        List<Maintenance> maintenances = maintenanceRepository.findByPolicyId(policyId);
        return maintenances.stream().anyMatch(
                m -> m.getStatus() == MaintenanceStatus.PROCESSING || m.getStatus() == MaintenanceStatus.PENDING);
    }

    @Override
    public Maintenance saveMaintenance(Maintenance maintenance) {
        return maintenanceRepository.save(maintenance);
    }

    @Override
    public void deleteMaintenance(Maintenance maintenance) {
        if (maintenance.getStatus() != MaintenanceStatus.PENDING) {
            throw new MaintenanceStatusException(maintenance.getId().getId(), maintenance.getStatus().name(), "DELETED",
                    "仅待处理状态的保全允许删除");
        }
        maintenanceRepository.delete(maintenance);
    }

    @Override
    public boolean isMaintenanceTypeExcluded(MaintenanceType existingType, MaintenanceType newType, String tenantId) {
        if (existingType == newType) {
            return false;
        }
        return maintenanceRepository.isMaintenanceTypeExcluded(existingType, newType, tenantId);
    }
}
