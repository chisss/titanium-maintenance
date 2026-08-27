package com.titanium.maintenance.infrastructure.repository.jpa;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.infrastructure.entity.MaintenanceItemConfigurationDO;

/** 保全项配置当前快照的 Spring Data 仓储。 */
public interface MaintenanceItemConfigurationJpaRepository
        extends JpaRepository<MaintenanceItemConfigurationDO, String> {

    boolean existsByTenantIdAndItemCodeAndConfigurationVersion(
            String tenantId, String itemCode, String configurationVersion);

    Optional<MaintenanceItemConfigurationDO> findByTenantIdAndConfigurationId(
            String tenantId, String configurationId);

    @Query("""
            SELECT c FROM MaintenanceItemConfigurationDO c
             WHERE c.tenantId = :tenantId
               AND (:itemCode IS NULL OR c.itemCode = :itemCode)
               AND (:status IS NULL OR c.status = :status)
               AND (:effectiveAt IS NULL OR (
                    c.validFrom <= :effectiveAt
                    AND (c.validTo IS NULL OR c.validTo > :effectiveAt)))
             ORDER BY c.updatedAt DESC, c.configurationId ASC
            """)
    Page<MaintenanceItemConfigurationDO> search(
            @Param("tenantId") String tenantId,
            @Param("itemCode") String itemCode,
            @Param("status") MaintenanceItemConfigurationStatus status,
            @Param("effectiveAt") LocalDateTime effectiveAt,
            Pageable pageable);

    @Query("""
            SELECT c FROM MaintenanceItemConfigurationDO c
             WHERE c.tenantId = :tenantId
               AND c.itemCode = :itemCode
               AND c.status = com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus.PUBLISHED
               AND c.validFrom <= :businessTime
               AND (c.validTo IS NULL OR c.validTo > :businessTime)
             ORDER BY c.validFrom DESC
            """)
    Optional<MaintenanceItemConfigurationDO> findEffective(
            @Param("tenantId") String tenantId,
            @Param("itemCode") String itemCode,
            @Param("businessTime") LocalDateTime businessTime);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END
              FROM MaintenanceItemConfigurationDO c
             WHERE c.tenantId = :tenantId
               AND c.itemCode = :itemCode
               AND c.configurationId <> :excludedConfigurationId
               AND c.status = com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus.PUBLISHED
               AND (:effectiveTo IS NULL OR c.validFrom < :effectiveTo)
               AND (c.validTo IS NULL OR c.validTo > :effectiveFrom)
            """)
    boolean existsPublishedOverlap(
            @Param("tenantId") String tenantId,
            @Param("itemCode") String itemCode,
            @Param("excludedConfigurationId") String excludedConfigurationId,
            @Param("effectiveFrom") LocalDateTime effectiveFrom,
            @Param("effectiveTo") LocalDateTime effectiveTo);
}
