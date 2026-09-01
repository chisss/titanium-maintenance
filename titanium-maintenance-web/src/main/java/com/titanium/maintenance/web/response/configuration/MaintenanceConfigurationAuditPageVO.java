package com.titanium.maintenance.web.response.configuration;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.titanium.maintenance.common.enums.config.MaintenanceConfigurationAction;

/** 配置前后快照与字段级差异的审计分页响应。 */
public record MaintenanceConfigurationAuditPageVO(
        List<ItemVO> items, long total, int page, int size, int totalPages) {

    public record ItemVO(
            String auditId,
            int sequence,
            MaintenanceConfigurationAction action,
            String operatorId,
            String detail,
            MaintenanceConfigurationVO before,
            MaintenanceConfigurationVO after,
            List<ChangeVO> changes,
            String beforeHash,
            String afterHash,
            String sourceIp,
            String correlationId,
            String operationResult,
            LocalDateTime occurredAt,
            LocalDateTime recordedAt) {
    }

    public record ChangeVO(String path, JsonNode beforeValue, JsonNode afterValue) {
    }
}
