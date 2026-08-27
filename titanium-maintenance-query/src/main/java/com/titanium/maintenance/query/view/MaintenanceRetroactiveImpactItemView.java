package com.titanium.maintenance.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 可筛选的追溯影响项读模型。 */
@Entity
@Table(name = "t_maintenance_retroactive_impact_view", indexes = {
        @Index(name = "idx_retro_impact_case", columnList = "tenant_id,maintenance_id,analysis_id"),
        @Index(name = "idx_retro_impact_filter", columnList = "tenant_id,source_domain,severity,handling_status")
})
@Getter
@Setter
public class MaintenanceRetroactiveImpactItemView extends BaseView {

    @Id
    @Column(name = "impact_record_id", nullable = false, length = 191)
    private String impactRecordId;

    @Column(name = "maintenance_id", nullable = false, length = 36)
    private String maintenanceId;

    @Column(name = "analysis_id", nullable = false, length = 64)
    private String analysisId;

    @Column(name = "analysis_version", nullable = false)
    private int analysisVersion;

    @Column(name = "item_id", nullable = false, length = 128)
    private String itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_domain", nullable = false, length = 16)
    private MaintenanceRetroactiveImpactDomain sourceDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_type", nullable = false, length = 32)
    private MaintenanceRetroactiveImpactType impactType;

    @Column(name = "reference_id", nullable = false, length = 128)
    private String referenceId;

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "source_status", nullable = false, length = 64)
    private String sourceStatus;

    @Column(name = "amount", precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(name = "currency", length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private MaintenanceRetroactiveImpactSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "handling_status", nullable = false, length = 16)
    private MaintenanceRetroactiveImpactItemStatus handlingStatus;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "evidence_version", nullable = false, length = 64)
    private String evidenceVersion;

    @Column(name = "evidence_hash", nullable = false, length = 64)
    private String evidenceHash;
}
