package com.titanium.maintenance.application.query;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.orchestration.configuration.MaintenanceConfigurationQueryApplicationService;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationAuditPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationSearchCriteria;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;

import lombok.RequiredArgsConstructor;

/** Web/API 可调用的保全项配置读侧入口。 */
@Service
@RequiredArgsConstructor
public class MaintenanceConfigurationQueryService {

    private final MaintenanceConfigurationQueryApplicationService queryService;

    public StoredConfiguration get(String tenantId, String configurationId) {
        return queryService.get(tenantId, configurationId);
    }

    public StoredConfiguration resolveEffective(
            String tenantId, String itemCode, LocalDateTime businessTime) {
        return queryService.resolveEffective(tenantId, itemCode, businessTime);
    }

    public ConfigurationPage search(String tenantId, ConfigurationSearchCriteria criteria) {
        return queryService.search(tenantId, criteria);
    }

    public ConfigurationAuditPage findAuditHistory(
            String tenantId, String configurationId, int page, int size) {
        return queryService.findAuditHistory(tenantId, configurationId, page, size);
    }
}
