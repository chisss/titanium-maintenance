package com.titanium.maintenance.infrastructure.repository.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.maintenance.infrastructure.entity.MaintenanceConfigurationAuditDO;

/** 仅供基础设施适配器追加配置审计记录。 */
public interface MaintenanceConfigurationAuditJpaRepository
        extends JpaRepository<MaintenanceConfigurationAuditDO, String> {

    Page<MaintenanceConfigurationAuditDO> findByTenantIdAndConfigurationIdOrderByAuditSequenceDesc(
            String tenantId, String configurationId, Pageable pageable);
}
