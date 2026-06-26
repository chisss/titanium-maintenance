package com.titanium.maintenance.repository;

import com.titanium.maintenance.aggregate.Maintenance;
import com.titanium.maintenance.enums.MaintenanceStatus;
import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.repository.jpa.MaintenanceCaseJpaRepository;
import com.titanium.maintenance.repository.jpa.MaintenanceChangeRecordJpaRepository;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class MaintenanceRepositoryImpl implements MaintenanceRepository {
    private final MaintenanceCaseJpaRepository maintenanceCaseJpaRepository;
    private final MaintenanceChangeRecordJpaRepository maintenanceChangeRecordJpaRepository;
    private final com.titanium.maintenance.repository.jpa.MaintenanceExclusionJpaRepository maintenanceExclusionJpaRepository;

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
        return maintenanceExclusionJpaRepository.findExclusionsByMaintenanceType(existingType, tenantId)
                .stream()
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
        return maintenanceCaseJpaRepository.findById(id.getId())
                .map(this::convertToAggregate);
    }

    @Override
    public List<Maintenance> findByPolicyId(PolicyId policyId) {
        return maintenanceCaseJpaRepository.findByPolicyId(policyId.getId())
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    public List<Maintenance> findByCustomerId(String customerId) {
        return maintenanceCaseJpaRepository.findByCustomerId(customerId)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Maintenance maintenance) {
        maintenanceCaseJpaRepository.deleteById(maintenance.getId().getId());
    }

    private Maintenance convertToAggregate(MaintenanceCaseJpaEntity entity) {
        Maintenance maintenance = new Maintenance();
        // 使用反射或Accessor来设置私有字段
        try {
            java.lang.reflect.Field idField = Maintenance.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(maintenance, MaintenanceId.of(entity.getId()));

            java.lang.reflect.Field policyIdField = Maintenance.class.getDeclaredField("policyId");
            policyIdField.setAccessible(true);
            policyIdField.set(maintenance, PolicyId.of(entity.getPolicyId()));

            java.lang.reflect.Field customerIdField = Maintenance.class.getDeclaredField("customerId");
            customerIdField.setAccessible(true);
            customerIdField.set(maintenance, CustomerId.of(entity.getCustomerId()));

            java.lang.reflect.Field maintenanceTypeField = Maintenance.class.getDeclaredField("maintenanceType");
            maintenanceTypeField.setAccessible(true);
            maintenanceTypeField.set(maintenance, entity.getMaintenanceType());

            java.lang.reflect.Field statusField = Maintenance.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(maintenance, entity.getStatus());

            java.lang.reflect.Field effectiveTimeTypeField = Maintenance.class.getDeclaredField("effectiveTimeType");
            effectiveTimeTypeField.setAccessible(true);
            effectiveTimeTypeField.set(maintenance, entity.getEffectiveTimeType());

            java.lang.reflect.Field specificEffectiveDateField = Maintenance.class.getDeclaredField("specificEffectiveDate");
            specificEffectiveDateField.setAccessible(true);
            specificEffectiveDateField.set(maintenance, entity.getSpecificEffectiveDate());

            java.lang.reflect.Field totalAmountField = Maintenance.class.getDeclaredField("totalAmount");
            totalAmountField.setAccessible(true);
            totalAmountField.set(maintenance, entity.getTotalAmount());

            java.lang.reflect.Field refundAmountField = Maintenance.class.getDeclaredField("refundAmount");
            refundAmountField.setAccessible(true);
            refundAmountField.set(maintenance, entity.getRefundAmount());

            java.lang.reflect.Field descriptionField = Maintenance.class.getDeclaredField("description");
            descriptionField.setAccessible(true);
            descriptionField.set(maintenance, entity.getDescription());

            java.lang.reflect.Field changesField = Maintenance.class.getDeclaredField("changes");
            changesField.setAccessible(true);
            changesField.set(maintenance, new ArrayList<>());

            java.lang.reflect.Field createdAtField = Maintenance.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(maintenance, entity.getCreatedAt());

            java.lang.reflect.Field createdByField = Maintenance.class.getDeclaredField("createdBy");
            createdByField.setAccessible(true);
            createdByField.set(maintenance, entity.getCreatedBy());

            java.lang.reflect.Field updatedAtField = Maintenance.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(maintenance, entity.getUpdatedAt());

            java.lang.reflect.Field updatedByField = Maintenance.class.getDeclaredField("updatedBy");
            updatedByField.setAccessible(true);
            updatedByField.set(maintenance, entity.getUpdatedBy());

            java.lang.reflect.Field tenantIdField = Maintenance.class.getDeclaredField("tenantId");
            tenantIdField.setAccessible(true);
            tenantIdField.set(maintenance, entity.getTenantId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JPA entity to aggregate", e);
        }
        return maintenance;
    }

    private MaintenanceCaseJpaEntity convertToEntity(Maintenance maintenance) {
        return MaintenanceCaseJpaEntity.builder()
                .id(maintenance.getId().getId())
                .policyId(maintenance.getPolicyId().getId())
                .customerId(maintenance.getCustomerId().getId())
                .maintenanceType(maintenance.getMaintenanceType())
                .status(maintenance.getStatus())
                .effectiveTimeType(maintenance.getEffectiveTimeType())
                .specificEffectiveDate(maintenance.getSpecificEffectiveDate())
                .totalAmount(maintenance.getTotalAmount())
                .refundAmount(maintenance.getRefundAmount())
                .description(maintenance.getDescription())
                .createdAt(maintenance.getCreatedAt())
                .createdBy(maintenance.getCreatedBy())
                .updatedAt(maintenance.getUpdatedAt())
                .updatedBy(maintenance.getUpdatedBy())
                .tenantId(maintenance.getTenantId())
                .build();
    }
}