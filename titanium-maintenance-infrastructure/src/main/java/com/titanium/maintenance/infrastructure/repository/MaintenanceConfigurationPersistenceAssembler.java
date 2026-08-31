package com.titanium.maintenance.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.configuration.MaintenanceConfigurationAuditEntry;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.infrastructure.entity.MaintenanceConfigurationAuditDO;
import com.titanium.maintenance.infrastructure.entity.MaintenanceItemConfigurationDO;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.SaveContext;

/**
 * 保全项配置持久化对象组装器（红线 21：>5 字段对象组装抽象专类）
 * <p>
 * 集中承载「领域配置 → 快照 DO」与「审计轨迹 → 审计 DO」的字段组装及组装期业务决策
 * （哈希归一化、审计序号、操作结果常量、记录时间取值），仓储适配器只保留事务与持久化编排。
 * </p>
 */
@Component
public class MaintenanceConfigurationPersistenceAssembler {

    /** 审计操作结果常量：本域审计落库一律记录成功 */
    private static final String OPERATION_SUCCESS = "SUCCESS";

    /**
     * 将配置快照写入持久化实体。
     * <p>
     * tenantId/configurationId/createdAt 属插入路径定位与首次时间语义，由仓储适配器按插入/更新路径维护，
     * 不在本方法内处理。
     * </p>
     */
    public void applySnapshot(MaintenanceItemConfigurationDO entity,
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

    /**
     * 将审计轨迹中自 {@code persistedAuditCount} 起的条目组装为审计持久化实体列表。
     * <p>
     * 组装期业务决策：审计序号按轨迹位置自增、auditId 每次随机生成、操作结果恒为
     * {@link #OPERATION_SUCCESS}、recordedAt 取本轮轨迹最后一条发生时间。
     * </p>
     */
    public List<MaintenanceConfigurationAuditDO> appendAudits(
            MaintenanceItemConfiguration configuration, int persistedAuditCount,
            String beforeJson, String afterJson, String beforeHash, String afterHash,
            SaveContext context) {
        List<MaintenanceConfigurationAuditDO> auditEntities = new ArrayList<>();
        List<MaintenanceConfigurationAuditEntry> auditTrail = configuration.getAuditTrail();
        LocalDateTime recordedAt = auditTrail.getLast().occurredAt();
        for (int index = persistedAuditCount; index < auditTrail.size(); index++) {
            auditEntities.add(toAuditEntity(configuration, auditTrail.get(index), index, recordedAt,
                    beforeJson, afterJson, beforeHash, afterHash, context));
        }
        return auditEntities;
    }

    /** 空哈希归一化为 null，避免空串污染唯一键/对比语义 */
    public String normalizeHash(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private MaintenanceConfigurationAuditDO toAuditEntity(
            MaintenanceItemConfiguration configuration, MaintenanceConfigurationAuditEntry entry,
            int index, LocalDateTime recordedAt, String beforeJson, String afterJson,
            String beforeHash, String afterHash, SaveContext context) {
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
        audit.setRecordedAt(recordedAt);
        return audit;
    }
}
