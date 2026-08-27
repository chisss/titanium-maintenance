package com.titanium.maintenance.configuration;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.config.MaintenanceConfigurationAction;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项配置生命周期的追加式审计记录。 */
public record MaintenanceConfigurationAuditEntry(MaintenanceConfigurationAction action, String operatorId,
        LocalDateTime occurredAt, String detail) {

    public MaintenanceConfigurationAuditEntry {
        if (action == null || occurredAt == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceConfigurationAuditEntry", "操作类型和时间不能为空");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceConfigurationAuditEntry", "operatorId", "操作人不能为空");
        }
        operatorId = operatorId.trim();
        detail = detail == null || detail.isBlank() ? null : detail.trim();
    }
}
