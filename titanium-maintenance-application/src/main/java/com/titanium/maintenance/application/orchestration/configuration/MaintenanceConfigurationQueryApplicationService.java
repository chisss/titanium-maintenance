package com.titanium.maintenance.application.orchestration.configuration;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.exception.MaintenanceConfigurationNotFoundException;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationAuditPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationSearchCriteria;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;

import lombok.RequiredArgsConstructor;

/** 保全项配置管理的租户隔离查询编排。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaintenanceConfigurationQueryApplicationService {

    private final MaintenanceItemConfigurationRepository repository;

    public StoredConfiguration get(String tenantId, String configurationId) {
        return repository.findById(tenantId, configurationId)
                .orElseThrow(MaintenanceConfigurationNotFoundException::new);
    }

    public StoredConfiguration resolveEffective(
            String tenantId, String itemCode, LocalDateTime businessTime) {
        return repository.findEffective(tenantId, itemCode, businessTime)
                .orElseThrow(MaintenanceConfigurationNotFoundException::new);
    }

    public ConfigurationPage search(String tenantId, ConfigurationSearchCriteria criteria) {
        return repository.search(tenantId, criteria);
    }

    public ConfigurationAuditPage findAuditHistory(
            String tenantId, String configurationId, int page, int size) {
        ConfigurationAuditPage result = repository.findAuditHistory(
                tenantId, configurationId, page, size);
        if (result.total() == 0) {
            throw new MaintenanceConfigurationNotFoundException();
        }
        return result;
    }
}
