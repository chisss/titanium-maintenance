package com.titanium.maintenance.infrastructure.entity;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.config.MaintenanceConfigurationAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 保全项配置变更前后快照的追加式审计实体。 */
@Entity
@Table(name = "t_maintenance_item_configuration_audit",
        uniqueConstraints = @UniqueConstraint(name = "uk_maintenance_config_audit_sequence",
                columnNames = {"tenant_id", "configuration_id", "audit_sequence"}),
        indexes = @Index(name = "idx_maintenance_config_audit_correlation",
                columnList = "tenant_id,correlation_id"))
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceConfigurationAuditDO {

    @Id
    @Column(name = "audit_id", nullable = false, length = 36)
    private String auditId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "configuration_id", nullable = false, length = 64)
    private String configurationId;

    @Column(name = "audit_sequence", nullable = false)
    private int auditSequence;

    @Column(name = "action", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private MaintenanceConfigurationAction action;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Lob
    @Column(name = "before_json", columnDefinition = "LONGTEXT")
    private String beforeJson;

    @Lob
    @Column(name = "after_json", nullable = false, columnDefinition = "LONGTEXT")
    private String afterJson;

    @Column(name = "before_hash", length = 64)
    private String beforeHash;

    @Column(name = "after_hash", length = 64)
    private String afterHash;

    @Column(name = "source_ip", nullable = false, length = 64)
    private String sourceIp;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "operation_result", nullable = false, length = 16)
    private String operationResult;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
