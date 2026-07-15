package com.titanium.maintenance.infrastructure.repository;

import org.springframework.stereotype.Repository;

import com.titanium.maintenance.infrastructure.repository.jpa.MaintenanceExclusionJpaRepository;
import com.titanium.maintenance.repository.MaintenanceExclusionRepository;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

import lombok.RequiredArgsConstructor;

/**
 * 保全类型互斥配置仓储实现（driven adapter）
 * <p>
 * 适配 {@link MaintenanceExclusionRepository} 端口，经 JPA 查询互斥配置表 {@code maintenance_exclusion}。
 * 属参考/配置数据读取，与写侧事件溯源聚合无关。
 * </p>
 */
@Repository
@RequiredArgsConstructor
public class MaintenanceExclusionRepositoryImpl implements MaintenanceExclusionRepository {

    private final MaintenanceExclusionJpaRepository maintenanceExclusionJpaRepository;

    @Override
    public boolean isMaintenanceTypeExcluded(MaintenanceType existingType, MaintenanceType newType, String tenantId) {
        // 同一保全类型不构成互斥
        if (existingType == newType) {
            return false;
        }
        return maintenanceExclusionJpaRepository.findExclusionsByMaintenanceType(existingType, tenantId).stream()
                .anyMatch(exclusion -> exclusion.getMaintenanceType1() == newType
                        || exclusion.getMaintenanceType2() == newType);
    }
}
