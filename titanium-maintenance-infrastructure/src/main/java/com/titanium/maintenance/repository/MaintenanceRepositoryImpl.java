package com.titanium.maintenance.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.titanium.maintenance.aggregate.Maintenance;
import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.repository.jpa.MaintenanceCaseJpaRepository;
import com.titanium.maintenance.repository.jpa.MaintenanceChangeRecordJpaRepository;
import com.titanium.maintenance.repository.jpa.MaintenanceExclusionJpaRepository;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;

@Repository
public class MaintenanceRepositoryImpl implements MaintenanceRepository {
    private final MaintenanceCaseJpaRepository         maintenanceCaseJpaRepository;
    private final MaintenanceChangeRecordJpaRepository maintenanceChangeRecordJpaRepository;
    private final MaintenanceExclusionJpaRepository    maintenanceExclusionJpaRepository;

    public MaintenanceRepositoryImpl(MaintenanceCaseJpaRepository maintenanceCaseJpaRepository,
                                     MaintenanceChangeRecordJpaRepository maintenanceChangeRecordJpaRepository,
                                     com.titanium.maintenance.repository.jpa.MaintenanceExclusionJpaRepository maintenanceExclusionJpaRepository) {
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
        MaintenanceCaseJpaEntity entity = convertToEntity(maintenance);
        MaintenanceCaseJpaEntity savedEntity = maintenanceCaseJpaRepository.save(entity);
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

    private Maintenance convertToAggregate(MaintenanceCaseJpaEntity entity) {
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

    private MaintenanceCaseJpaEntity convertToEntity(Maintenance maintenance) {
        return MaintenanceCaseJpaEntity.builder().id(maintenance.getId().getId())
                .policyId(maintenance.getPolicyId().getId()).customerId(maintenance.getCustomerId().getId())
                .maintenanceType(maintenance.getMaintenanceType()).status(maintenance.getStatus())
                .effectiveTimeType(maintenance.getEffectiveTimeType())
                .specificEffectiveDate(maintenance.getSpecificEffectiveDate()).totalAmount(maintenance.getTotalAmount())
                .refundAmount(maintenance.getRefundAmount()).description(maintenance.getDescription())
                .createdAt(maintenance.getCreateTime()).createdBy(maintenance.getCreatedBy())
                .updatedAt(maintenance.getUpdateTime()).updatedBy(maintenance.getUpdatedBy())
                .tenantId(maintenance.getTenantId()).build();
    }
}
