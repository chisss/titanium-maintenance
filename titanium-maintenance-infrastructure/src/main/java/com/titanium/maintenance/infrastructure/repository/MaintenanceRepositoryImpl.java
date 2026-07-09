package com.titanium.maintenance.infrastructure.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.titanium.maintenance.aggregate.Maintenance;
import com.titanium.maintenance.infrastructure.entity.MaintenanceCaseEntity;
import com.titanium.maintenance.infrastructure.repository.jpa.MaintenanceCaseJpaRepository;
import com.titanium.maintenance.infrastructure.repository.jpa.MaintenanceChangeRecordJpaRepository;
import com.titanium.maintenance.infrastructure.repository.jpa.MaintenanceExclusionJpaRepository;
import com.titanium.maintenance.repository.MaintenanceRepository;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

@Repository
public class MaintenanceRepositoryImpl implements MaintenanceRepository {
    private final MaintenanceCaseJpaRepository         maintenanceCaseJpaRepository;
    private final MaintenanceChangeRecordJpaRepository maintenanceChangeRecordJpaRepository;
    private final MaintenanceExclusionJpaRepository    maintenanceExclusionJpaRepository;

    public MaintenanceRepositoryImpl(MaintenanceCaseJpaRepository maintenanceCaseJpaRepository,
                                     MaintenanceChangeRecordJpaRepository maintenanceChangeRecordJpaRepository,
                                     MaintenanceExclusionJpaRepository maintenanceExclusionJpaRepository) {
        this.maintenanceCaseJpaRepository = maintenanceCaseJpaRepository;
        this.maintenanceChangeRecordJpaRepository = maintenanceChangeRecordJpaRepository;
        this.maintenanceExclusionJpaRepository = maintenanceExclusionJpaRepository;
    }

    @Override
    public boolean isMaintenanceTypeExcluded(MaintenanceType existingType, MaintenanceType newType, String tenantId) {
        if (existingType == newType) {
            return false;
        }
        return maintenanceExclusionJpaRepository.findExclusionsByMaintenanceType(existingType, tenantId).stream()
                .anyMatch(exclusion -> exclusion.getMaintenanceType1() == newType
                        || exclusion.getMaintenanceType2() == newType);
    }

    @Override
    public Maintenance save(Maintenance maintenance) {
        MaintenanceCaseEntity entity = convertToEntity(maintenance);
        MaintenanceCaseEntity savedEntity = maintenanceCaseJpaRepository.save(entity);
        return convertToAggregate(savedEntity);
    }

    @Override
    public Optional<Maintenance> findById(MaintenanceId id) {
        return maintenanceCaseJpaRepository.findById(id.getId()).map(this::convertToAggregate);
    }

    @Override
    public List<Maintenance> findByPolicyId(PolicyId policyId) {
        return maintenanceCaseJpaRepository.findByPolicyId(policyId.getId()).stream().map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    public List<Maintenance> findByCustomerId(String customerId) {
        return maintenanceCaseJpaRepository.findByCustomerId(customerId).stream().map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Maintenance maintenance) {
        maintenanceCaseJpaRepository.deleteById(maintenance.getId().getId());
    }

    private Maintenance convertToAggregate(MaintenanceCaseEntity entity) {
        // 经 SuperBuilder 构建聚合，统一填充自身字段与基类审计字段(tenantId/createTime/updateTime)
        return Maintenance.builder()
                .id(MaintenanceId.of(entity.getId()))
                .policyId(PolicyId.of(entity.getPolicyId()))
                .customerId(CustomerId.of(entity.getCustomerId()))
                .maintenanceType(entity.getMaintenanceType())
                .status(entity.getStatus())
                .effectiveTimeType(entity.getEffectiveTimeType())
                .specificEffectiveDate(entity.getSpecificEffectiveDate())
                .totalAmount(entity.getTotalAmount())
                .refundAmount(entity.getRefundAmount())
                .description(entity.getDescription())
                .changes(new ArrayList<>())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .tenantId(entity.getTenantId())
                .createTime(entity.getCreatedAt())
                .updateTime(entity.getUpdatedAt())
                .build();
    }

    private MaintenanceCaseEntity convertToEntity(Maintenance maintenance) {
        MaintenanceCaseEntity entity = new MaintenanceCaseEntity();
        entity.setId(maintenance.getId().getId());
        entity.setPolicyId(maintenance.getPolicyId().getId());
        entity.setCustomerId(maintenance.getCustomerId().getId());
        entity.setMaintenanceType(maintenance.getMaintenanceType());
        entity.setStatus(maintenance.getStatus());
        entity.setEffectiveTimeType(maintenance.getEffectiveTimeType());
        entity.setSpecificEffectiveDate(maintenance.getSpecificEffectiveDate());
        entity.setTotalAmount(maintenance.getTotalAmount());
        entity.setRefundAmount(maintenance.getRefundAmount());
        entity.setDescription(maintenance.getDescription());
        entity.setCreatedAt(maintenance.getCreateTime());
        entity.setCreatedBy(maintenance.getCreatedBy());
        entity.setUpdatedAt(maintenance.getUpdateTime());
        entity.setUpdatedBy(maintenance.getUpdatedBy());
        entity.setTenantId(maintenance.getTenantId());
        return entity;
    }
}
