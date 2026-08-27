package com.titanium.maintenance.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalFundAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 独立保全案件中已冻结的保全项及其配置、Offering 证据。 */
@Entity
@Table(name = "t_maintenance_case_item_view")
@Getter
@Setter
public class MaintenanceCaseItemView extends BaseView {

    @Id
    @Column(name = "item_view_id", nullable = false, length = 191)
    private String itemViewId;

    @Column(name = "maintenance_id", nullable = false, length = 64)
    private String maintenanceId;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 128)
    private String itemName;

    @Column(name = "item_category", nullable = false, length = 32)
    private String itemCategory;

    @Column(name = "configuration_id", length = 64)
    private String configurationId;

    @Column(name = "configuration_version", nullable = false, length = 64)
    private String configurationVersion;

    @Column(name = "configuration_content_hash", length = 64)
    private String configurationContentHash;

    @Column(name = "offering_id", length = 64)
    private String offeringId;

    @Column(name = "offering_version", length = 64)
    private String offeringVersion;

    @Column(name = "offering_content_hash", length = 64)
    private String offeringContentHash;

    @Column(name = "evidence_resolved_at", length = 40)
    private String evidenceResolvedAt;

    @Column(name = "selected_at", nullable = false)
    private LocalDateTime selectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_status", length = 32)
    private MaintenanceItemWithdrawalStatus withdrawalStatus;

    @Column(name = "withdrawal_operation_id", length = 128)
    private String withdrawalOperationId;

    @Column(name = "withdrawal_request_hash", length = 64)
    private String withdrawalRequestHash;

    @Column(name = "withdrawal_reason", length = 500)
    private String withdrawalReason;

    @Column(name = "withdrawal_payment_method", length = 64)
    private String withdrawalPaymentMethod;

    @Column(name = "withdrawal_recovery_configured_at")
    private LocalDateTime withdrawalRecoveryConfiguredAt;

    @Column(name = "withdrawal_source_posting_id", length = 64)
    private String withdrawalSourcePostingId;

    @Column(name = "withdrawal_source_result_hash", length = 64)
    private String withdrawalSourceResultHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_source_direction", length = 16)
    private MaintenanceBalanceDirection withdrawalSourceDirection;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_source_fund_status", length = 20)
    private MaintenanceFundSettlementStatus withdrawalSourceFundStatus;

    @Column(name = "withdrawal_reversal_id", length = 64)
    private String withdrawalReversalId;

    @Column(name = "withdrawal_reversal_result_hash", length = 64)
    private String withdrawalReversalResultHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_reversal_direction", length = 16)
    private MaintenanceBalanceDirection withdrawalReversalDirection;

    @Column(name = "withdrawal_amount", precision = 20, scale = 8)
    private BigDecimal withdrawalAmount;

    @Column(name = "withdrawal_currency", length = 3)
    private String withdrawalCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_fund_action", length = 20)
    private MaintenanceItemWithdrawalFundAction withdrawalFundAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_fund_status", length = 20)
    private MaintenanceFundSettlementStatus withdrawalFundStatus;

    @Column(name = "withdrawal_fund_request_id", length = 64)
    private String withdrawalFundRequestId;

    @Column(name = "withdrawal_fund_order_id", length = 64)
    private String withdrawalFundOrderId;

    @Column(name = "withdrawal_fund_external_status", length = 32)
    private String withdrawalFundExternalStatus;

    @Column(name = "withdrawal_failure_code", length = 64)
    private String withdrawalFailureCode;

    @Column(name = "withdrawal_failure_message", length = 500)
    private String withdrawalFailureMessage;

    @Column(name = "withdrawal_retry_count")
    private Integer withdrawalRetryCount;

    @Column(name = "withdrawal_requested_at")
    private LocalDateTime withdrawalRequestedAt;

    @Column(name = "withdrawal_completed_at")
    private LocalDateTime withdrawalCompletedAt;

    @Column(name = "withdrawal_requested_by", length = 64)
    private String withdrawalRequestedBy;

    @Column(name = "withdrawal_updated_by", length = 64)
    private String withdrawalUpdatedBy;

    @Column(name = "withdrawal_recovery_lease_owner", length = 128)
    private String withdrawalRecoveryLeaseOwner;

    @Column(name = "withdrawal_recovery_lease_until")
    private LocalDateTime withdrawalRecoveryLeaseUntil;
}
