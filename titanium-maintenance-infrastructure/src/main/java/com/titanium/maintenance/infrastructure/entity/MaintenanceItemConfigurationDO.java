package com.titanium.maintenance.infrastructure.entity;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 保全项配置当前版本持久化实体。 */
@Entity
@Table(name = "t_maintenance_item_configuration",
        uniqueConstraints = @UniqueConstraint(name = "uk_maintenance_config_business_key",
                columnNames = {"tenant_id", "item_code", "configuration_version"}),
        indexes = {
                @Index(name = "idx_maintenance_config_effective",
                        columnList = "tenant_id,item_code,status,valid_from,valid_to")
        })
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceItemConfigurationDO {

    @Id
    @Column(name = "configuration_id", nullable = false, length = 64)
    private String configurationId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "configuration_version", nullable = false, length = 64)
    private String configurationVersion;

    @Column(name = "revision_of_configuration_id", length = 64)
    private String revisionOfConfigurationId;

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private MaintenanceItemConfigurationStatus status;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Lob
    @Column(name = "configuration_json", nullable = false, columnDefinition = "LONGTEXT")
    private String configurationJson;

    @Column(name = "audit_entry_count", nullable = false)
    private int auditEntryCount;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
