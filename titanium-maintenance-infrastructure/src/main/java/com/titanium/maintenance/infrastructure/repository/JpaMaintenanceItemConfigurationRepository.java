package com.titanium.maintenance.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.common.exception.MaintenanceConfigurationConflictException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationPreconditionFailedException;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.infrastructure.entity.MaintenanceConfigurationAuditDO;
import com.titanium.maintenance.infrastructure.entity.MaintenanceItemConfigurationDO;
import com.titanium.maintenance.infrastructure.repository.jpa.MaintenanceConfigurationAuditJpaRepository;
import com.titanium.maintenance.infrastructure.repository.jpa.MaintenanceItemConfigurationJpaRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;

import lombok.RequiredArgsConstructor;

/** 基于 JPA 的保全项配置聚合仓储适配器。 */
@Repository
@RequiredArgsConstructor
public class JpaMaintenanceItemConfigurationRepository
        implements MaintenanceItemConfigurationRepository {

    private final MaintenanceItemConfigurationJpaRepository configurationJpaRepository;
    private final MaintenanceConfigurationAuditJpaRepository auditJpaRepository;
    private final MaintenanceItemConfigurationJsonMapper jsonMapper;
    private final MaintenanceConfigurationPersistenceAssembler persistenceAssembler;

    @Override
    public boolean existsByBusinessKey(
            String tenantId, String itemCode, String configurationVersion) {
        return configurationJpaRepository.existsByTenantIdAndItemCodeAndConfigurationVersion(
                tenantId, itemCode, configurationVersion);
    }

    @Override
    public Optional<StoredConfiguration> findById(String tenantId, String configurationId) {
        return configurationJpaRepository.findByTenantIdAndConfigurationId(tenantId, configurationId)
                .map(this::toStoredConfiguration);
    }

    @Override
    public Optional<StoredConfiguration> findEffective(
            String tenantId, String itemCode, LocalDateTime businessTime) {
        return configurationJpaRepository.findEffective(tenantId, itemCode, businessTime)
                .map(this::toStoredConfiguration);
    }

    @Override
    public ConfigurationPage search(String tenantId, ConfigurationSearchCriteria criteria) {
        Page<MaintenanceItemConfigurationDO> result = configurationJpaRepository.search(
                tenantId, criteria.itemCode(), criteria.status(), criteria.effectiveAt(),
                PageRequest.of(criteria.page(), criteria.size()));
        return new ConfigurationPage(
                result.getContent().stream().map(this::toStoredConfiguration).toList(),
                result.getTotalElements(), criteria.page(), criteria.size());
    }

    @Override
    public ConfigurationAuditPage findAuditHistory(
            String tenantId, String configurationId, int page, int size) {
        Page<MaintenanceConfigurationAuditDO> result = auditJpaRepository
                .findByTenantIdAndConfigurationIdOrderByAuditSequenceDesc(
                        tenantId, configurationId, PageRequest.of(page, size));
        return new ConfigurationAuditPage(
                result.getContent().stream().map(this::toAuditRecord).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public boolean existsPublishedOverlap(String tenantId, String itemCode,
            String excludedConfigurationId, LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        return configurationJpaRepository.existsPublishedOverlap(
                tenantId, itemCode, excludedConfigurationId, effectiveFrom, effectiveTo);
    }

    @Override
    @Transactional
    public StoredConfiguration save(MaintenanceItemConfiguration configuration,
            long expectedRowVersion, SaveContext context) {
        try {
            return expectedRowVersion == NEW_CONFIGURATION_VERSION
                    ? insert(configuration, context)
                    : update(configuration, expectedRowVersion, context);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new MaintenanceConfigurationPreconditionFailedException();
        } catch (DataIntegrityViolationException exception) {
            throw conflict(exception);
        }
    }

    @Override
    @Transactional
    public void deleteDraft(MaintenanceItemConfiguration configuration,
            long expectedRowVersion, SaveContext context) {
        try {
            MaintenanceItemConfigurationDO entity = configurationJpaRepository
                    .findByTenantIdAndConfigurationId(
                            configuration.getTenantId(), configuration.getConfigurationId())
                    .orElseThrow(MaintenanceConfigurationNotFoundException::new);
            if (entity.getRowVersion() == null || entity.getRowVersion() != expectedRowVersion) {
                throw new MaintenanceConfigurationPreconditionFailedException();
            }
            int persistedAuditCount = entity.getAuditEntryCount();
            if (configuration.getAuditTrail().size() <= persistedAuditCount) {
                throw new MaintenanceConfigurationConflictException("配置删除缺少新增审计记录");
            }
            String afterJson = jsonMapper.toJson(configuration);
            appendAudits(configuration, persistedAuditCount, entity.getConfigurationJson(), afterJson,
                    persistenceAssembler.normalizeHash(entity.getContentHash()),
                    persistenceAssembler.normalizeHash(configuration.getContentHash()), context);
            configurationJpaRepository.delete(entity);
            configurationJpaRepository.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new MaintenanceConfigurationPreconditionFailedException();
        } catch (DataIntegrityViolationException exception) {
            throw conflict(exception);
        }
    }

    private StoredConfiguration insert(
            MaintenanceItemConfiguration configuration, SaveContext context) {
        if (configurationJpaRepository.existsById(configuration.getConfigurationId())) {
            throw new MaintenanceConfigurationConflictException("配置 ID 已存在");
        }
        String afterJson = jsonMapper.toJson(configuration);
        MaintenanceItemConfigurationDO entity = new MaintenanceItemConfigurationDO();
        entity.setConfigurationId(configuration.getConfigurationId());
        entity.setTenantId(configuration.getTenantId());
        entity.setCreatedAt(configuration.getAuditTrail().getFirst().occurredAt());
        persistenceAssembler.applySnapshot(entity, configuration, afterJson);
        MaintenanceItemConfigurationDO saved = configurationJpaRepository.saveAndFlush(entity);
        appendAudits(configuration, 0, null, afterJson, null,
                persistenceAssembler.normalizeHash(configuration.getContentHash()), context);
        return toStoredConfiguration(saved);
    }

    private StoredConfiguration update(MaintenanceItemConfiguration configuration,
            long expectedRowVersion, SaveContext context) {
        MaintenanceItemConfigurationDO entity = configurationJpaRepository
                .findByTenantIdAndConfigurationId(
                        configuration.getTenantId(), configuration.getConfigurationId())
                .orElseThrow(MaintenanceConfigurationNotFoundException::new);
        if (entity.getRowVersion() == null || entity.getRowVersion() != expectedRowVersion) {
            throw new MaintenanceConfigurationPreconditionFailedException();
        }
        int persistedAuditCount = entity.getAuditEntryCount();
        if (configuration.getAuditTrail().size() <= persistedAuditCount) {
            throw new MaintenanceConfigurationConflictException("配置保存缺少新增审计记录");
        }
        String beforeJson = entity.getConfigurationJson();
        String beforeHash = persistenceAssembler.normalizeHash(entity.getContentHash());
        String afterJson = jsonMapper.toJson(configuration);
        persistenceAssembler.applySnapshot(entity, configuration, afterJson);
        MaintenanceItemConfigurationDO saved = configurationJpaRepository.saveAndFlush(entity);
        appendAudits(configuration, persistedAuditCount, beforeJson, afterJson, beforeHash,
                persistenceAssembler.normalizeHash(configuration.getContentHash()), context);
        return toStoredConfiguration(saved);
    }

    private void appendAudits(MaintenanceItemConfiguration configuration, int persistedAuditCount,
            String beforeJson, String afterJson, String beforeHash, String afterHash, SaveContext context) {
        auditJpaRepository.saveAll(persistenceAssembler.appendAudits(
                configuration, persistedAuditCount, beforeJson, afterJson, beforeHash, afterHash, context));
    }

    private StoredConfiguration toStoredConfiguration(MaintenanceItemConfigurationDO entity) {
        long rowVersion = entity.getRowVersion() == null ? 0L : entity.getRowVersion();
        return new StoredConfiguration(jsonMapper.fromJson(entity.getConfigurationJson()), rowVersion);
    }

    private ConfigurationAuditRecord toAuditRecord(MaintenanceConfigurationAuditDO entity) {
        MaintenanceItemConfiguration before = entity.getBeforeJson() == null
                ? null : jsonMapper.fromJson(entity.getBeforeJson());
        return new ConfigurationAuditRecord(
                entity.getAuditId(), entity.getAuditSequence(), entity.getAction(),
                entity.getOperatorId(), entity.getDetail(), before,
                jsonMapper.fromJson(entity.getAfterJson()), entity.getBeforeHash(), entity.getAfterHash(),
                entity.getSourceIp(), entity.getCorrelationId(), entity.getOperationResult(),
                entity.getOccurredAt(), entity.getRecordedAt());
    }

    private MaintenanceConfigurationConflictException conflict(RuntimeException exception) {
        return new MaintenanceConfigurationConflictException(
                "保全项配置发生唯一键或乐观锁冲突: " + exception.getClass().getSimpleName());
    }
}
