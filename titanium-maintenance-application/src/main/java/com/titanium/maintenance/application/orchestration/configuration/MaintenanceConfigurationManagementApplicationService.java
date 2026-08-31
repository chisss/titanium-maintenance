package com.titanium.maintenance.application.orchestration.configuration;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationOperationContext;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationCriteria;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationResult;
import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationConflictException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationFeatureDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationPreconditionFailedException;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenancePublicationEvidence;
import com.titanium.maintenance.port.MaintenanceConfigurationFeaturePort;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.SaveContext;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;

import lombok.RequiredArgsConstructor;

/** 保全项配置生命周期的应用层编排入口。 */
@Service
@RequiredArgsConstructor
public class MaintenanceConfigurationManagementApplicationService {

    private final MaintenanceItemConfigurationRepository repository;
    private final MaintenanceConfigurationValidator validator;
    private final MaintenanceConfigurationFeaturePort featurePort;

    @Transactional
    public StoredConfiguration createDraft(String configurationId, MaintenanceItemDefinition definition,
            LocalDateTime validFrom, LocalDateTime validTo,
            MaintenanceConfigurationOperationContext context) {
        requireWriteEnabled(context.tenantId());
        ensureBusinessKeyAvailable(context.tenantId(), definition);
        MaintenanceItemConfiguration configuration = MaintenanceItemConfiguration.createDraft(
                configurationId, context.tenantId(), definition, validFrom, validTo,
                context.operatorId(), context.operatedAt());
        return saveNew(configuration, context);
    }

    @Transactional
    public StoredConfiguration updateDraft(String configurationId, long expectedRowVersion,
            MaintenanceItemDefinition replacement, LocalDateTime validFrom, LocalDateTime validTo,
            MaintenanceConfigurationOperationContext context) {
        StoredConfiguration stored = load(configurationId, expectedRowVersion, context.tenantId());
        stored.configuration().replaceDraftContent(
                replacement, validFrom, validTo, context.operatorId(), context.operatedAt());
        return save(stored.configuration(), expectedRowVersion, context);
    }

    @Transactional
    public StoredConfiguration submitForApproval(String configurationId, long expectedRowVersion,
            MaintenanceConfigurationValidationCriteria criteria,
            MaintenanceConfigurationOperationContext context) {
        StoredConfiguration stored = load(configurationId, expectedRowVersion, context.tenantId());
        if (stored.configuration().getStatus() == MaintenanceItemConfigurationStatus.PENDING_APPROVAL) {
            return stored;
        }
        stored.configuration().getDefinition().validateForSubmission();
        validator.validateAndRequire(context.tenantId(), stored.configuration().getDefinition(),
                criteria, context.operatedAt());
        stored.configuration().submitForApproval(context.operatorId(), context.operatedAt());
        return save(stored.configuration(), expectedRowVersion, context);
    }

    @Transactional
    public StoredConfiguration approve(String configurationId, long expectedRowVersion,
            MaintenanceConfigurationValidationCriteria criteria,
            MaintenanceConfigurationOperationContext context) {
        StoredConfiguration stored = load(configurationId, expectedRowVersion, context.tenantId());
        validator.validateAndRequire(context.tenantId(), stored.configuration().getDefinition(),
                criteria, context.operatedAt());
        stored.configuration().approve(context.operatorId(), context.operatedAt());
        return save(stored.configuration(), expectedRowVersion, context);
    }

    @Transactional
    public StoredConfiguration reject(String configurationId, long expectedRowVersion, String reason,
            MaintenanceConfigurationOperationContext context) {
        StoredConfiguration stored = load(configurationId, expectedRowVersion, context.tenantId());
        stored.configuration().reject(context.operatorId(), reason, context.operatedAt());
        return save(stored.configuration(), expectedRowVersion, context);
    }

    @Transactional
    public StoredConfiguration returnToDraft(String configurationId, long expectedRowVersion, String reason,
            MaintenanceConfigurationOperationContext context) {
        StoredConfiguration stored = load(configurationId, expectedRowVersion, context.tenantId());
        stored.configuration().returnToDraft(context.operatorId(), reason, context.operatedAt());
        return save(stored.configuration(), expectedRowVersion, context);
    }

    @Transactional
    public StoredConfiguration publish(String configurationId, long expectedRowVersion,
            MaintenanceConfigurationValidationCriteria criteria,
            MaintenanceConfigurationOperationContext context) {
        StoredConfiguration stored = load(configurationId, expectedRowVersion, context.tenantId());
        MaintenanceItemConfiguration configuration = stored.configuration();
        if (configuration.getStatus() == MaintenanceItemConfigurationStatus.PUBLISHED) {
            return stored;
        }
        requireWriteEnabled(context.tenantId());
        MaintenanceConfigurationValidationResult validation = validator.validateAndRequire(
                context.tenantId(), configuration.getDefinition(), criteria, context.operatedAt());
        if (repository.existsPublishedOverlap(context.tenantId(), configuration.getDefinition().itemCode(),
                configuration.getConfigurationId(), configuration.getValidFrom(), configuration.getValidTo())) {
            throw new MaintenanceConfigurationConflictException("同一保全项存在有效期重叠的已发布配置");
        }
        configuration.publish(context.operatorId(), context.operatedAt(),
                new MaintenancePublicationEvidence(validation.catalogVersion(),
                        validation.catalogHash(), validation.validatedAt()));
        return save(configuration, expectedRowVersion, context);
    }

    @Transactional
    public StoredConfiguration retire(String configurationId, long expectedRowVersion,
            MaintenanceConfigurationOperationContext context) {
        StoredConfiguration stored = load(configurationId, expectedRowVersion, context.tenantId());
        if (stored.configuration().getStatus() == MaintenanceItemConfigurationStatus.RETIRED) {
            return stored;
        }
        stored.configuration().retire(context.operatorId(), context.operatedAt());
        return save(stored.configuration(), expectedRowVersion, context);
    }

    @Transactional
    public void deleteDraft(String configurationId, long expectedRowVersion,
            MaintenanceConfigurationOperationContext context) {
        StoredConfiguration stored = load(configurationId, expectedRowVersion, context.tenantId());
        stored.configuration().recordDraftDeletion(context.operatorId(), context.operatedAt());
        repository.deleteDraft(stored.configuration(), expectedRowVersion,
                new SaveContext(context.sourceIp(), context.correlationId()));
    }

    @Transactional
    public StoredConfiguration createRevision(String sourceConfigurationId, long expectedSourceRowVersion,
            String newConfigurationId, String newVersion, LocalDateTime validFrom, LocalDateTime validTo,
            MaintenanceConfigurationOperationContext context) {
        requireWriteEnabled(context.tenantId());
        StoredConfiguration source = load(
                sourceConfigurationId, expectedSourceRowVersion, context.tenantId());
        if (repository.existsByBusinessKey(context.tenantId(),
                source.configuration().getDefinition().itemCode(), newVersion)) {
            throw new MaintenanceConfigurationConflictException("保全项编码与配置版本已存在");
        }
        MaintenanceItemConfiguration revision = source.configuration().createRevision(
                newConfigurationId, newVersion, validFrom, validTo,
                context.operatorId(), context.operatedAt());
        return saveNew(revision, context);
    }

    @Transactional(readOnly = true)
    public MaintenanceConfigurationValidationResult validate(String configurationId,
            MaintenanceConfigurationValidationCriteria criteria,
            MaintenanceConfigurationOperationContext context) {
        StoredConfiguration stored = repository.findById(context.tenantId(), configurationId)
                .orElseThrow(MaintenanceConfigurationNotFoundException::new);
        stored.configuration().getDefinition().validateForSubmission();
        return validator.validate(context.tenantId(), stored.configuration().getDefinition(),
                criteria, context.operatedAt());
    }

    private StoredConfiguration load(String configurationId, long expectedRowVersion, String tenantId) {
        StoredConfiguration stored = repository.findById(tenantId, configurationId)
                .orElseThrow(MaintenanceConfigurationNotFoundException::new);
        if (stored.rowVersion() != expectedRowVersion) {
            throw new MaintenanceConfigurationPreconditionFailedException();
        }
        return stored;
    }

    private void ensureBusinessKeyAvailable(String tenantId, MaintenanceItemDefinition definition) {
        if (repository.existsByBusinessKey(tenantId, definition.itemCode(), definition.version())) {
            throw new MaintenanceConfigurationConflictException("保全项编码与配置版本已存在");
        }
    }

    private void requireWriteEnabled(String tenantId) {
        if (!featurePort.isWriteEnabled(tenantId)) {
            throw new MaintenanceConfigurationFeatureDisabledException();
        }
    }

    private StoredConfiguration saveNew(MaintenanceItemConfiguration configuration,
            MaintenanceConfigurationOperationContext context) {
        return save(configuration, MaintenanceItemConfigurationRepository.NEW_CONFIGURATION_VERSION, context);
    }

    private StoredConfiguration save(MaintenanceItemConfiguration configuration, long expectedRowVersion,
            MaintenanceConfigurationOperationContext context) {
        return repository.save(configuration, expectedRowVersion,
                new SaveContext(context.sourceIp(), context.correlationId()));
    }
}
