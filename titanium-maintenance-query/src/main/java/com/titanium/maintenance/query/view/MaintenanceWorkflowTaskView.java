package com.titanium.maintenance.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.query.converter.LocalDateTimeStringConverter;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 保全案件流程任务投影。 */
@Entity
@Table(name = "t_maintenance_workflow_task_view")
@Getter
@Setter
public class MaintenanceWorkflowTaskView extends BaseView {

    @Id
    @Column(name = "task_id", nullable = false, length = 191)
    private String taskId;

    @Column(name = "maintenance_id", nullable = false, length = 64)
    private String maintenanceId;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "item_order", nullable = false)
    private int itemOrder;

    @Column(name = "step_sequence", nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 32)
    private MaintenanceStepType stepType;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_mode", nullable = false, length = 16)
    private MaintenanceStepMode mode;

    @Column(name = "condition_rule_code", length = 128)
    private String conditionRuleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", nullable = false, length = 32)
    private MaintenanceWorkflowTaskStatus status;

    @Column(name = "assigned_to", length = 64)
    private String assignedTo;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "condition_rule_version", length = 64)
    private String conditionRuleVersion;

    @Column(name = "condition_input_hash", length = 64)
    private String conditionInputHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_decision", length = 16)
    private MaintenanceWorkflowConditionDecision conditionDecision;

    @Column(name = "condition_reason", length = 500)
    private String conditionReason;

    @Column(name = "condition_decided_at")
    private LocalDateTime conditionDecidedAt;

    @Column(name = "condition_decided_by", length = 64)
    private String conditionDecidedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_mode", length = 16)
    private MaintenanceReviewMode reviewMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_decision", length = 16)
    private MaintenanceReviewDecision reviewDecision;

    @Column(name = "review_policy_code", length = 128)
    private String reviewPolicyCode;

    @Column(name = "review_policy_version", length = 64)
    private String reviewPolicyVersion;

    @Column(name = "review_context_hash", length = 64)
    private String reviewContextHash;

    @Column(name = "review_gate_evidence_json", columnDefinition = "TEXT")
    private String reviewGateEvidenceJson;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "review_decided_at")
    private LocalDateTime reviewDecidedAt;

    @Column(name = "review_decided_by", length = 64)
    private String reviewDecidedBy;

    @Column(name = "underwriting_case_id", length = 64)
    private String underwritingCaseId;

    @Column(name = "underwriting_request_hash", length = 64)
    private String underwritingRequestHash;

    @Column(name = "underwriting_rule_version", length = 64)
    private String underwritingRuleVersion;

    @Column(name = "underwriting_model_version", length = 64)
    private String underwritingModelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "underwriting_conclusion", length = 32)
    private MaintenanceUnderwritingConclusion underwritingConclusion;

    @Column(name = "underwriting_conditions_json", columnDefinition = "TEXT")
    private String underwritingConditionsJson;

    @Column(name = "underwriting_summary", length = 500)
    private String underwritingSummary;

    @Column(name = "underwriting_completed_at")
    private LocalDateTime underwritingCompletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "premium_quote_status", length = 32)
    private MaintenancePremiumQuoteStatus premiumQuoteStatus;

    @Column(name = "premium_quote_id", length = 64)
    private String premiumQuoteId;

    @Column(name = "premium_quote_version", length = 64)
    private String premiumQuoteVersion;

    @Column(name = "premium_quote_request_hash", length = 64)
    private String premiumQuoteRequestHash;

    @Column(name = "premium_quote_original_calculation_id", length = 128)
    private String premiumQuoteOriginalCalculationId;

    @Column(name = "premium_quote_original_result_hash", length = 64)
    private String premiumQuoteOriginalResultHash;

    @Column(name = "premium_quote_replacement_calculation_id", length = 128)
    private String premiumQuoteReplacementCalculationId;

    @Column(name = "premium_quote_replacement_result_hash", length = 64)
    private String premiumQuoteReplacementResultHash;

    @Column(name = "premium_quote_pricing_plan_version", length = 64)
    private String premiumQuotePricingPlanVersion;

    @Column(name = "premium_quote_pricing_plan_hash", length = 64)
    private String premiumQuotePricingPlanHash;

    @Column(name = "premium_quote_result_hash", length = 64)
    private String premiumQuoteResultHash;

    @Column(name = "premium_quote_detail_summary", length = 500)
    private String premiumQuoteDetailSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "premium_quote_direction", length = 16)
    private MaintenanceBalanceDirection premiumQuoteDirection;

    @Column(name = "premium_quote_amount", precision = 20, scale = 8)
    private BigDecimal premiumQuoteAmount;

    @Column(name = "premium_quote_currency", length = 3)
    private String premiumQuoteCurrency;

    @Column(name = "premium_quoted_at")
    private LocalDateTime premiumQuotedAt;

    @Column(name = "premium_quote_valid_until")
    private LocalDateTime premiumQuoteValidUntil;

    @Column(name = "billing_posting_id", length = 64)
    private String billingPostingId;

    @Column(name = "billing_adjustment_id", length = 64)
    private String billingAdjustmentId;

    @Column(name = "billing_result_hash", length = 64)
    private String billingResultHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_posting_direction", length = 16)
    private MaintenanceBalanceDirection billingPostingDirection;

    @Column(name = "billing_posting_amount", precision = 20, scale = 8)
    private BigDecimal billingPostingAmount;

    @Column(name = "billing_posting_currency", length = 3)
    private String billingPostingCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_posting_status", length = 16)
    private MaintenanceBillingPostingStatus billingPostingStatus;

    @Column(name = "billing_commission_adjustment_count")
    private Integer billingCommissionAdjustmentCount;

    @Column(name = "billing_posted_at")
    private LocalDateTime billingPostedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "fund_settlement_type", length = 16)
    private MaintenanceFundSettlementType fundSettlementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "fund_settlement_status", length = 16)
    private MaintenanceFundSettlementStatus fundSettlementStatus;

    @Column(name = "fund_source_posting_id", length = 64)
    private String fundSourcePostingId;

    @Column(name = "fund_instruction_id", length = 64)
    private String fundSettlementInstructionId;

    @Column(name = "fund_order_id", length = 64)
    private String fundSettlementOrderId;

    @Column(name = "fund_external_status", length = 32)
    private String fundSettlementExternalStatus;

    @Column(name = "fund_amount", precision = 20, scale = 8)
    private BigDecimal fundSettlementAmount;

    @Column(name = "fund_currency", length = 3)
    private String fundSettlementCurrency;

    @Column(name = "fund_failure_code", length = 64)
    private String fundSettlementFailureCode;

    @Column(name = "fund_failure_message", length = 500)
    private String fundSettlementFailureMessage;

    @Column(name = "fund_recorded_at")
    private LocalDateTime fundSettlementRecordedAt;

    @Column(name = "effect_request_id", length = 128)
    private String effectRequestId;

    @Column(name = "effect_request_hash", length = 64)
    private String effectRequestHash;

    @Column(name = "effect_expected_policy_version")
    private Long effectExpectedPolicyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect_time_type", length = 20)
    private EffectiveTimeType effectTimeType;

    @Convert(converter = LocalDateTimeStringConverter.class)
    @Column(name = "effect_requested_effective_at", length = 40)
    private LocalDateTime effectRequestedEffectiveAt;

    @Column(name = "effect_proposed_snapshot_hash", length = 64)
    private String effectProposedSnapshotHash;

    @Column(name = "effect_requested_at")
    private LocalDateTime effectRequestedAt;

    @Column(name = "policy_endorsement_no", length = 64)
    private String policyEndorsementNo;

    @Column(name = "policy_actual_version")
    private Long policyActualVersion;

    @Column(name = "policy_application_hash", length = 64)
    private String policyApplicationHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_state_action", length = 20)
    private PolicyMaintenanceAction policyStateAction;

    @Column(name = "policy_status_before", length = 32)
    private String policyStatusBefore;

    @Column(name = "policy_status_after", length = 32)
    private String policyStatusAfter;

    @Column(name = "applied_snapshot_storage_key", length = 512)
    private String appliedSnapshotStorageKey;

    @Column(name = "applied_snapshot_hash", length = 64)
    private String appliedSnapshotHash;

    @Column(name = "applied_snapshot_policy_version")
    private Long appliedSnapshotPolicyVersion;

    @Column(name = "applied_snapshot_captured_at", length = 40)
    private String appliedSnapshotCapturedAt;

    @Column(name = "applied_fields_json", columnDefinition = "TEXT")
    private String appliedFieldsJson;

    @Column(name = "policy_applied_at")
    private LocalDateTime policyAppliedAt;

    @Column(name = "last_operation_id", length = 128)
    private String lastOperationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_operation_action", length = 32)
    private MaintenanceWorkflowAction lastOperationAction;

    @Column(name = "last_operation_hash", length = 64)
    private String lastOperationHash;

    @Column(name = "last_evidence_version", length = 64)
    private String lastEvidenceVersion;

    @Column(name = "last_evidence_hash", length = 64)
    private String lastEvidenceHash;

    @Column(name = "last_result_code", length = 64)
    private String lastResultCode;

    @Column(name = "last_operation_reason", length = 500)
    private String lastOperationReason;

    @Column(name = "last_operated_at")
    private LocalDateTime lastOperatedAt;

    @Column(name = "last_operated_by", length = 64)
    private String lastOperatedBy;
}
