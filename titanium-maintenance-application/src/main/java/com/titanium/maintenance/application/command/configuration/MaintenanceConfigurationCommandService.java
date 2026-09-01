package com.titanium.maintenance.application.command.configuration;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationOperationContext;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationCriteria;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationResult;
import com.titanium.maintenance.application.orchestration.configuration.MaintenanceConfigurationManagementApplicationService;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;

import lombok.RequiredArgsConstructor;

/** Web/API 可调用的保全项配置写侧入口。 */
@Service
@RequiredArgsConstructor
public class MaintenanceConfigurationCommandService {

    private final MaintenanceConfigurationManagementApplicationService managementService;

    public StoredConfiguration createDraft(String configurationId, MaintenanceItemDefinition definition,
            LocalDateTime validFrom, LocalDateTime validTo,
            MaintenanceConfigurationOperationContext context) {
        return managementService.createDraft(configurationId, definition, validFrom, validTo, context);
    }

    public StoredConfiguration updateDraft(String configurationId, long expectedRowVersion,
            MaintenanceItemDefinition replacement, LocalDateTime validFrom, LocalDateTime validTo,
            MaintenanceConfigurationOperationContext context) {
        return managementService.updateDraft(
                configurationId, expectedRowVersion, replacement, validFrom, validTo, context);
    }

    public StoredConfiguration submitForApproval(String configurationId, long expectedRowVersion,
            MaintenanceConfigurationValidationCriteria criteria,
            MaintenanceConfigurationOperationContext context) {
        return managementService.submitForApproval(configurationId, expectedRowVersion, criteria, context);
    }

    public StoredConfiguration approve(String configurationId, long expectedRowVersion,
            MaintenanceConfigurationValidationCriteria criteria,
            MaintenanceConfigurationOperationContext context) {
        return managementService.approve(configurationId, expectedRowVersion, criteria, context);
    }

    public StoredConfiguration reject(String configurationId, long expectedRowVersion, String reason,
            MaintenanceConfigurationOperationContext context) {
        return managementService.reject(configurationId, expectedRowVersion, reason, context);
    }

    public StoredConfiguration returnToDraft(String configurationId, long expectedRowVersion, String reason,
            MaintenanceConfigurationOperationContext context) {
        return managementService.returnToDraft(configurationId, expectedRowVersion, reason, context);
    }

    public StoredConfiguration publish(String configurationId, long expectedRowVersion,
            MaintenanceConfigurationValidationCriteria criteria,
            MaintenanceConfigurationOperationContext context) {
        return managementService.publish(configurationId, expectedRowVersion, criteria, context);
    }

    public StoredConfiguration retire(String configurationId, long expectedRowVersion,
            MaintenanceConfigurationOperationContext context) {
        return managementService.retire(configurationId, expectedRowVersion, context);
    }

    public void deleteDraft(String configurationId, long expectedRowVersion,
            MaintenanceConfigurationOperationContext context) {
        managementService.deleteDraft(configurationId, expectedRowVersion, context);
    }

    public StoredConfiguration createRevision(String sourceConfigurationId, long expectedSourceRowVersion,
            String newConfigurationId, String newVersion, LocalDateTime validFrom, LocalDateTime validTo,
            MaintenanceConfigurationOperationContext context) {
        return managementService.createRevision(
                sourceConfigurationId, expectedSourceRowVersion, newConfigurationId, newVersion,
                validFrom, validTo, context);
    }

    public MaintenanceConfigurationValidationResult validate(String configurationId,
            MaintenanceConfigurationValidationCriteria criteria,
            MaintenanceConfigurationOperationContext context) {
        return managementService.validate(configurationId, criteria, context);
    }
}
