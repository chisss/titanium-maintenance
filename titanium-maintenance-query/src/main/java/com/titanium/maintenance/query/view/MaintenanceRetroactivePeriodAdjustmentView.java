package com.titanium.maintenance.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Product 与 Billing 可逐期间核对的追溯调整读模型。 */
@Entity
@Table(name = "t_maintenance_retroactive_period_adjustment_view", indexes = {
        @Index(name = "idx_retro_period_case", columnList = "tenant_id,maintenance_id,period_recalculation_id"),
        @Index(name = "idx_retro_period_status", columnList = "tenant_id,billing_status,accounting_period")
})
@Getter
@Setter
public class MaintenanceRetroactivePeriodAdjustmentView extends BaseView {

    @Id
    @Column(name = "period_record_id", nullable = false, length = 191)
    private String periodRecordId;

    @Column(name = "maintenance_id", nullable = false, length = 64)
    private String maintenanceId;

    @Column(name = "period_recalculation_id", nullable = false, length = 64)
    private String periodRecalculationId;

    @Column(name = "period_recalculation_version", nullable = false)
    private int periodRecalculationVersion;

    @Column(name = "analysis_id", nullable = false, length = 64)
    private String analysisId;

    @Column(name = "analysis_version", nullable = false)
    private int analysisVersion;

    @Column(name = "period_id", nullable = false, length = 128)
    private String periodId;

    @Column(name = "source_reference_id", nullable = false, length = 128)
    private String sourceReferenceId;

    @Column(name = "accounting_period", length = 16)
    private String accountingPeriod;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "original_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal originalAmount;

    @Column(name = "recalculated_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal recalculatedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private MaintenanceBalanceDirection direction;

    @Column(name = "difference_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal differenceAmount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "billing_status", length = 32)
    private String billingStatus;

    @Column(name = "source_evidence_hash", nullable = false, length = 64)
    private String sourceEvidenceHash;

    @Column(name = "product_result_hash", nullable = false, length = 64)
    private String productResultHash;

    @Column(name = "billing_result_hash", length = 64)
    private String billingResultHash;

    @Column(name = "target_accounting_period", length = 16)
    private String targetAccountingPeriod;

    @Column(name = "resolution_status", length = 24)
    private String resolutionStatus;

    @Column(name = "posting_reference", length = 128)
    private String postingReference;

    @Column(name = "resolution_result_hash", length = 64)
    private String resolutionResultHash;
}
