package com.titanium.maintenance.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.configuration.MaintenanceConfigurationAuditEntry;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenancePublicationEvidence;
import com.titanium.maintenance.exception.MaintenanceConfigurationValidationException;

import lombok.RequiredArgsConstructor;

/** 保全项配置聚合与稳定 JSON 快照之间的映射器。 */
@Component
@RequiredArgsConstructor
public class MaintenanceItemConfigurationJsonMapper {

    private final ObjectMapper objectMapper;

    public String toJson(MaintenanceItemConfiguration configuration) {
        ConfigurationSnapshot snapshot = new ConfigurationSnapshot(
                configuration.getConfigurationId(), configuration.getTenantId(),
                configuration.getRevisionOfConfigurationId(), configuration.getDefinition(),
                configuration.getValidFrom(), configuration.getValidTo(), configuration.getStatus(),
                configuration.getContentHash(), configuration.getPublicationEvidence(),
                configuration.getAuditTrail());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new MaintenanceConfigurationValidationException("保全项配置 JSON 序列化失败");
        }
    }

    public MaintenanceItemConfiguration fromJson(String json) {
        try {
            ConfigurationSnapshot snapshot = objectMapper.readValue(json, ConfigurationSnapshot.class);
            return MaintenanceItemConfiguration.restore(
                    snapshot.configurationId(), snapshot.tenantId(), snapshot.revisionOfConfigurationId(),
                    snapshot.definition(), snapshot.validFrom(), snapshot.validTo(), snapshot.status(),
                    snapshot.contentHash(), snapshot.publicationEvidence(), snapshot.auditTrail());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new MaintenanceConfigurationValidationException("保全项配置 JSON 反序列化失败");
        }
    }

    private record ConfigurationSnapshot(String configurationId, String tenantId,
            String revisionOfConfigurationId, MaintenanceItemDefinition definition,
            LocalDateTime validFrom, LocalDateTime validTo,
            MaintenanceItemConfigurationStatus status, String contentHash,
            MaintenancePublicationEvidence publicationEvidence,
            List<MaintenanceConfigurationAuditEntry> auditTrail) {
    }
}
