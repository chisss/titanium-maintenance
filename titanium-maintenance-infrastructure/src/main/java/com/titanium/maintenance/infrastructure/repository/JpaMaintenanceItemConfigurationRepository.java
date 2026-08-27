package com.titanium.maintenance.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.configuration.MaintenanceConfigurationAuditEntry;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.exception.MaintenanceConfigurationConflictException;
import com.titanium.maintenance.exception.MaintenanceConfigurationNotFoundException;
import com.titanium.maintenance.exception.MaintenanceConfigurationPreconditionFailedException;
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

    private static final String OPERATION_SUCCESS = "SUCCESS";

    private final MaintenanceItemConfigurationJpaRepository configurationJpaRepository;
    private final MaintenanceConfigurationAuditJpaRepository auditJpaRepository;
    private final MaintenanceItemConfigurationJsonMapper jsonMapper;

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
                    normalizeHash(entity.getContentHash()), normalizeHash(configuration.getContentHash()), context);
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
        applySnapshot(entity, configuration, afterJson);
        MaintenanceItemConfigurationDO saved = configurationJpaRepository.saveAndFlush(entity);
        appendAudits(configuration, 0, null, afterJson, null,
                normalizeHash(configuration.getContentHash()), context);
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
        String beforeHash = normalizeHash(entity.getContentHash());
        String afterJson = jsonMapper.toJson(configuration);
        applySnapshot(entity, configuration, afterJson);
        MaintenanceItemConfigurationDO saved = configurationJpaRepository.saveAndFlush(entity);
        appendAudits(configuration, persistedAuditCount, beforeJson, afterJson, beforeHash,
                normalizeHash(configuration.getContentHash()), context);
        return toStoredConfiguration(saved);
    }

    private void applySnapshot(MaintenanceItemConfigurationDO entity,
            MaintenanceItemConfiguration configuration, String configurationJson) {
        entity.setItemCode(configuration.getDefinition().itemCode());
        entity.setConfigurationVersion(configuration.getDefinition().version());
        entity.setRevisionOfConfigurationId(configuration.getRevisionOfConfigurationId());
        entity.setStatus(configuration.getStatus());
        entity.setValidFrom(configuration.getValidFrom());
        entity.setValidTo(configuration.getValidTo());
        entity.setContentHash(normalizeHash(configuration.getContentHash()));
        entity.setConfigurationJson(configurationJson);
        entity.setAuditEntryCount(configuration.getAuditTrail().size());
        entity.setUpdatedAt(configuration.getAuditTrail().getLast().occurredAt());
    }

    private void appendAudits(MaintenanceItemConfiguration configuration, int persistedAuditCount,
            String beforeJson, String afterJson, String beforeHash, String afterHash, SaveContext context) {
        List<MaintenanceConfigurationAuditDO> auditEntities = new ArrayList<>();
        List<MaintenanceConfigurationAuditEntry> auditTrail = configuration.getAuditTrail();
        for (int index = persistedAuditCount; index < auditTrail.size(); index++) {
            MaintenanceConfigurationAuditEntry entry = auditTrail.get(index);
            MaintenanceConfigurationAuditDO audit = new MaintenanceConfigurationAuditDO();
            audit.setAuditId(UUID.randomUUID().toString());
            audit.setTenantId(configuration.getTenantId());
            audit.setConfigurationId(configuration.getConfigurationId());
            audit.setAuditSequence(index + 1);
            audit.setAction(entry.action());
            audit.setOperatorId(entry.operatorId());
            audit.setDetail(entry.detail());
            audit.setBeforeJson(beforeJson);
            audit.setAfterJson(afterJson);
            audit.setBeforeHash(beforeHash);
            audit.setAfterHash(afterHash);
            audit.setSourceIp(context.sourceIp());
            audit.setCorrelationId(context.correlationId());
            audit.setOperationResult(OPERATION_SUCCESS);
            audit.setOccurredAt(entry.occurredAt());
            audit.setRecordedAt(configuration.getAuditTrail().getLast().occurredAt());
            auditEntities.add(audit);
        }
        auditJpaRepository.saveAll(auditEntities);
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

    private String normalizeHash(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private MaintenanceConfigurationConflictException conflict(RuntimeException exception) {
        return new MaintenanceConfigurationConflictException(
                "保全项配置发生唯一键或乐观锁冲突: " + exception.getClass().getSimpleName());
    }
}
