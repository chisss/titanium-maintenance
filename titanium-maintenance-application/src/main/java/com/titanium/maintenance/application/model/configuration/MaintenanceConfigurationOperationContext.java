package com.titanium.maintenance.application.model.configuration;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项配置操作的租户、操作者与审计上下文。 */
public record MaintenanceConfigurationOperationContext(String tenantId, String operatorId,
        String sourceIp, String correlationId, LocalDateTime operatedAt) {

    public MaintenanceConfigurationOperationContext {
        tenantId = requireText("tenantId", tenantId);
        operatorId = requireText("operatorId", operatorId);
        sourceIp = requireText("sourceIp", sourceIp);
        correlationId = requireText("correlationId", correlationId);
        if (operatedAt == null) {
            throw validation("operatedAt", "操作时间不能为空");
        }
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceConfigurationOperationContext", fieldName, message);
    }
}
