package com.titanium.maintenance.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenancePremiumSettlementStatus;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保全案件读模型实体（CQRS Projection）
 * <p>
 * 对应读模型表 {@code t_maintenance_view}，与写侧事件存储物理隔离。 由
 * {@link com.titanium.maintenance.query.handler.projection.MaintenanceProjectionEventHandler} 订阅领域事件投影而来。
 * </p>
 * <p>
 * 继承 {@link BaseView}，复用租户ID、创建/更新时间（投影时间）、乐观锁版本等读模型公共字段。
 * </p>
 */
@Entity
@Table(name = "t_maintenance_view")
@Getter
@Setter
public class MaintenanceView extends BaseView {

    /** 保全案件ID（聚合根ID，读模型主键） */
    @Id
    @Column(name = "maintenance_id", nullable = false, length = 36)
    private String            maintenanceId;

    /** 保单ID */
    @Column(name = "policy_id", length = 36)
    private String            policyId;

    /** 客户ID */
    @Column(name = "customer_id", length = 36)
    private String            customerId;

    /** 独立案件来源与幂等定位信息；旧案件保持为空。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "case_source", length = 16)
    private MaintenanceChannel source;

    @Column(name = "client_request_key", length = 128)
    private String clientRequestKey;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    @Column(name = "independent_case", nullable = false)
    private boolean independentCase;

    @Column(name = "initialization_completed", nullable = false)
    private boolean initializationCompleted;

    @Column(name = "initialization_completed_at")
    private LocalDateTime initializationCompletedAt;

    @Column(name = "planned_item_count", nullable = false)
    private int plannedItemCount;

    /** Policy 建案基准及产品版本证据。 */
    @Column(name = "policy_number", length = 64)
    private String policyNumber;

    @Column(name = "product_id", length = 64)
    private String productId;

    @Column(name = "product_version", length = 64)
    private String productVersion;

    @Column(name = "plan_version", length = 64)
    private String planVersion;

    @Column(name = "policy_baseline_version")
    private Long policyBaselineVersion;

    @Column(name = "business_effective_at", length = 40)
    private String businessEffectiveAt;

    /** 保全类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_type", length = 50)
    private MaintenanceType   maintenanceType;

    /** 保全状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private MaintenanceStatus status;

    /** 与流程状态正交的 Policy 生效状态；旧案件按未开始解释。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "effect_status", nullable = false, length = 20)
    private MaintenanceEffectStatus effectStatus = MaintenanceEffectStatus.NOT_STARTED;

    /** 未来生效计划及可靠调度检查点。 */
    @Column(name = "effect_schedule_id", length = 128)
    private String effectScheduleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect_schedule_status", length = 20)
    private MaintenanceEffectScheduleStatus effectScheduleStatus;

    @Column(name = "effect_schedule_tenant_zone_id", length = 64)
    private String effectScheduleTenantZoneId;

    @Column(name = "effect_schedule_next_execution_at")
    private LocalDateTime effectScheduleNextExecutionAt;

    @Column(name = "effect_schedule_attempt_count", nullable = false)
    private int effectScheduleAttemptCount;

    @Column(name = "effect_schedule_last_attempt_id", length = 128)
    private String effectScheduleLastAttemptId;

    @Column(name = "effect_schedule_last_attempt_at")
    private LocalDateTime effectScheduleLastAttemptAt;

    @Column(name = "effect_schedule_last_error_code", length = 64)
    private String effectScheduleLastErrorCode;

    @Column(name = "effect_schedule_last_error_message", length = 500)
    private String effectScheduleLastErrorMessage;

    @Column(name = "effect_schedule_created_at")
    private LocalDateTime effectScheduleCreatedAt;

    @Column(name = "effect_schedule_updated_at")
    private LocalDateTime effectScheduleUpdatedAt;

    @Column(name = "effect_schedule_lease_owner", length = 128)
    private String effectScheduleLeaseOwner;

    @Column(name = "effect_schedule_lease_until")
    private LocalDateTime effectScheduleLeaseUntil;

    @Column(name = "effect_compensation_required", nullable = false)
    private boolean effectCompensationRequired;

    @Column(name = "effect_compensation_id", length = 128)
    private String effectCompensationId;

    @Column(name = "effect_compensation_request_id", length = 128)
    private String effectCompensationRequestId;

    @Column(name = "effect_compensation_endorsement_no", length = 64)
    private String effectCompensationEndorsementNo;

    @Column(name = "effect_compensation_policy_version")
    private Long effectCompensationPolicyVersion;

    @Column(name = "effect_compensation_application_hash", length = 64)
    private String effectCompensationApplicationHash;

    @Column(name = "effect_compensation_reason", length = 500)
    private String effectCompensationReason;

    @Column(name = "effect_compensation_recorded_at")
    private LocalDateTime effectCompensationRecordedAt;

    @Column(name = "effect_compensation_resolved_at")
    private LocalDateTime effectCompensationResolvedAt;

    @Column(name = "effect_compensation_resolved_by", length = 64)
    private String effectCompensationResolvedBy;

    /** 生效时间类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "effective_time_type", length = 20)
    private EffectiveTimeType effectiveTimeType;

    /** 指定生效日期 */
    @Column(name = "specific_effective_date")
    private LocalDateTime     specificEffectiveDate;

    /** 当前追溯影响分析摘要；结构化明细保存在独立读模型。 */
    @Column(name = "retroactive_impact_analysis_id", length = 64)
    private String retroactiveImpactAnalysisId;

    @Column(name = "retroactive_impact_analysis_version")
    private Integer retroactiveImpactAnalysisVersion;

    @Column(name = "retroactive_impact_operation_id", length = 128)
    private String retroactiveImpactOperationId;

    @Column(name = "retroactive_impact_request_hash", length = 64)
    private String retroactiveImpactRequestHash;

    @Column(name = "retroactive_impact_scope_from")
    private LocalDateTime retroactiveImpactScopeFrom;

    @Column(name = "retroactive_impact_scope_to")
    private LocalDateTime retroactiveImpactScopeTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "retroactive_impact_status", length = 20)
    private MaintenanceRetroactiveImpactAnalysisStatus retroactiveImpactStatus;

    @Column(name = "retroactive_impact_covered_domains", length = 255)
    private String retroactiveImpactCoveredDomains;

    @Column(name = "retroactive_impact_item_count", nullable = false)
    private int retroactiveImpactItemCount;

    @Column(name = "retroactive_impact_blocking_count", nullable = false)
    private int retroactiveImpactBlockingCount;

    @Column(name = "retroactive_impact_pending_count", nullable = false)
    private int retroactiveImpactPendingCount;

    @Column(name = "retroactive_impact_evidence_version", length = 64)
    private String retroactiveImpactEvidenceVersion;

    @Column(name = "retroactive_impact_result_hash", length = 64)
    private String retroactiveImpactResultHash;

    @Column(name = "retroactive_impact_failure_code", length = 64)
    private String retroactiveImpactFailureCode;

    @Column(name = "retroactive_impact_failure_message", length = 500)
    private String retroactiveImpactFailureMessage;

    @Column(name = "retroactive_impact_started_at")
    private LocalDateTime retroactiveImpactStartedAt;

    @Column(name = "retroactive_impact_completed_at")
    private LocalDateTime retroactiveImpactCompletedAt;

    @Column(name = "retroactive_impact_updated_at")
    private LocalDateTime retroactiveImpactUpdatedAt;

    @Column(name = "retroactive_period_recalculation_id", length = 64)
    private String retroactivePeriodRecalculationId;

    @Column(name = "retroactive_period_recalculation_version")
    private Integer retroactivePeriodRecalculationVersion;

    @Column(name = "retroactive_period_recalculation_operation_id", length = 128)
    private String retroactivePeriodRecalculationOperationId;

    @Column(name = "retroactive_period_recalculation_request_hash", length = 64)
    private String retroactivePeriodRecalculationRequestHash;

    @Column(name = "retroactive_period_analysis_id", length = 64)
    private String retroactivePeriodAnalysisId;

    @Column(name = "retroactive_period_analysis_version")
    private Integer retroactivePeriodAnalysisVersion;

    @Column(name = "retroactive_period_analysis_result_hash", length = 64)
    private String retroactivePeriodAnalysisResultHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "retroactive_period_recalculation_status", length = 24)
    private MaintenanceRetroactivePeriodRecalculationStatus retroactivePeriodRecalculationStatus;

    @Column(name = "retroactive_product_recalculation_id", length = 64)
    private String retroactiveProductRecalculationId;

    @Column(name = "retroactive_product_recalculation_version", length = 64)
    private String retroactiveProductRecalculationVersion;

    @Column(name = "retroactive_product_original_calculation_id", length = 128)
    private String retroactiveProductOriginalCalculationId;

    @Column(name = "retroactive_product_original_result_hash", length = 64)
    private String retroactiveProductOriginalResultHash;

    @Column(name = "retroactive_product_replacement_calculation_id", length = 128)
    private String retroactiveProductReplacementCalculationId;

    @Column(name = "retroactive_product_replacement_result_hash", length = 64)
    private String retroactiveProductReplacementResultHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "retroactive_product_direction", length = 16)
    private MaintenanceBalanceDirection retroactiveProductDirection;

    @Column(name = "retroactive_product_amount", precision = 20, scale = 8)
    private BigDecimal retroactiveProductAmount;

    @Column(name = "retroactive_product_currency", length = 8)
    private String retroactiveProductCurrency;

    @Column(name = "retroactive_product_input_hash", length = 64)
    private String retroactiveProductInputHash;

    @Column(name = "retroactive_product_result_hash", length = 64)
    private String retroactiveProductResultHash;

    @Column(name = "retroactive_product_calculated_at")
    private LocalDateTime retroactiveProductCalculatedAt;

    @Column(name = "retroactive_period_count", nullable = false)
    private int retroactivePeriodCount;

    @Column(name = "retroactive_billing_batch_id", length = 64)
    private String retroactiveBillingBatchId;

    @Column(name = "retroactive_billing_status", length = 32)
    private String retroactiveBillingStatus;

    @Column(name = "retroactive_billing_posted_count", nullable = false)
    private int retroactiveBillingPostedCount;

    @Column(name = "retroactive_billing_review_count", nullable = false)
    private int retroactiveBillingReviewCount;

    @Column(name = "retroactive_billing_request_hash", length = 64)
    private String retroactiveBillingRequestHash;

    @Column(name = "retroactive_billing_result_hash", length = 64)
    private String retroactiveBillingResultHash;

    @Column(name = "retroactive_billing_adjusted_at")
    private LocalDateTime retroactiveBillingAdjustedAt;

    @Column(name = "retroactive_period_failure_code", length = 64)
    private String retroactivePeriodFailureCode;

    @Column(name = "retroactive_period_failure_message", length = 500)
    private String retroactivePeriodFailureMessage;

    @Column(name = "retroactive_period_started_at")
    private LocalDateTime retroactivePeriodStartedAt;

    @Column(name = "retroactive_period_completed_at")
    private LocalDateTime retroactivePeriodCompletedAt;

    @Column(name = "retroactive_period_updated_at")
    private LocalDateTime retroactivePeriodUpdatedAt;

    @Column(name = "retroactive_period_resolution_id", length = 64)
    private String retroactivePeriodResolutionId;

    @Column(name = "retroactive_period_resolution_operation_id", length = 128)
    private String retroactivePeriodResolutionOperationId;

    @Column(name = "retroactive_period_resolution_request_hash", length = 64)
    private String retroactivePeriodResolutionRequestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "retroactive_period_resolution_status", length = 24)
    private MaintenanceRetroactivePeriodResolutionStatus retroactivePeriodResolutionStatus;

    @Column(name = "retroactive_billing_resolution_id", length = 64)
    private String retroactiveBillingResolutionId;

    @Column(name = "retroactive_period_resolution_source_batch_hash", length = 64)
    private String retroactivePeriodResolutionSourceBatchHash;

    @Column(name = "retroactive_period_resolution_target_period", length = 16)
    private String retroactivePeriodResolutionTargetPeriod;

    @Column(name = "retroactive_period_resolution_resolved_line_count", nullable = false)
    private int retroactivePeriodResolutionResolvedLineCount;

    @Column(name = "retroactive_period_resolution_result_hash", length = 64)
    private String retroactivePeriodResolutionResultHash;

    @Column(name = "retroactive_period_resolution_reason", length = 500)
    private String retroactivePeriodResolutionReason;

    @Column(name = "retroactive_period_resolution_failure_code", length = 64)
    private String retroactivePeriodResolutionFailureCode;

    @Column(name = "retroactive_period_resolution_failure_message", length = 500)
    private String retroactivePeriodResolutionFailureMessage;

    @Column(name = "retroactive_period_resolution_started_at")
    private LocalDateTime retroactivePeriodResolutionStartedAt;

    @Column(name = "retroactive_period_resolution_completed_at")
    private LocalDateTime retroactivePeriodResolutionCompletedAt;

    @Column(name = "retroactive_period_resolution_updated_at")
    private LocalDateTime retroactivePeriodResolutionUpdatedAt;

    /** 保全总金额 */
    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal        totalAmount;

    /** 退费金额 */
    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal        refundAmount;

    /** Product/Billing 生命周期费用事实及资金结算检查点状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "premium_settlement_status", length = 32)
    private MaintenancePremiumSettlementStatus premiumSettlementStatus;

    @Column(name = "original_calculation_id", length = 64)
    private String originalCalculationId;

    @Column(name = "replacement_calculation_id", length = 64)
    private String replacementCalculationId;

    /** 多个费用任务指向不同计算链时置为 true，禁止按任意任务执行追溯重算。 */
    @Column(name = "premium_calculation_checkpoint_conflict", nullable = false)
    private boolean premiumCalculationCheckpointConflict;

    @Column(name = "premium_adjustment_id", length = 64)
    private String premiumAdjustmentId;

    @Column(name = "premium_adjustment_result_hash", length = 64)
    private String premiumAdjustmentResultHash;

    @Column(name = "billing_posting_id", length = 64)
    private String billingPostingId;

    @Column(name = "refund_instruction_id", length = 64)
    private String refundInstructionId;

    @Column(name = "refund_order_id", length = 64)
    private String refundOrderId;

    @Column(name = "refund_status", length = 32)
    private String refundStatus;

    @Column(name = "commission_adjustment_count", nullable = false)
    private Integer commissionAdjustmentCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "balance_direction", length = 16)
    private MaintenanceBalanceDirection balanceDirection;

    @Column(name = "balance_amount", precision = 18, scale = 2)
    private BigDecimal balanceAmount;

    @Column(name = "balance_currency", length = 3)
    private String balanceCurrency;

    @Column(name = "surrender_policy_code", length = 64)
    private String surrenderPolicyCode;

    @Column(name = "surrender_policy_version", length = 32)
    private String surrenderPolicyVersion;

    @Column(name = "surrender_policy_content_hash", length = 64)
    private String surrenderPolicyContentHash;

    @Column(name = "surrender_policy_year")
    private Integer surrenderPolicyYear;

    @Column(name = "cooling_off_days")
    private Integer coolingOffDays;

    @Column(name = "surrender_refund_type", length = 32)
    private String surrenderRefundType;

    @Column(name = "within_cooling_off")
    private Boolean withinCoolingOff;

    @Column(name = "cash_value_rate", precision = 20, scale = 8)
    private BigDecimal cashValueRate;

    @Column(name = "retained_customer_amount", precision = 20, scale = 8)
    private BigDecimal retainedCustomerAmount;

    @Column(name = "internal_cost_retention_rate", precision = 20, scale = 8)
    private BigDecimal internalCostRetentionRate;

    /** 保全描述 */
    @Column(name = "description", length = 500)
    private String            description;

    /** 创建人（保全创建事件投影写入） */
    @Column(name = "created_by", length = 64)
    private String            createdBy;

    /** 更新人（保费计算/状态变更/执行等事件投影写入最近操作人） */
    @Column(name = "updated_by", length = 64)
    private String            updatedBy;

    /**
     * 判断案件是否可进入操作后台查询和既有操作入口。
     *
     * @return 旧案件始终可见；独立案件仅在原子建案初始化完成后可见
     */
    public boolean isOperatorVisible() {
        return !independentCase || initializationCompleted;
    }
}
