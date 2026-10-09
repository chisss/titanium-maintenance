package com.titanium.maintenance.query.view;

import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 独立案件逐对象、逐字段的 base/current/proposed/applied 查询投影。 */
@Entity
@Table(name = "t_maintenance_field_change_view")
@Getter
@Setter
public class MaintenanceFieldChangeView extends BaseView {

    @Id
    @Column(name = "field_change_id", nullable = false, length = 64)
    private String fieldChangeId;

    @Column(name = "maintenance_id", nullable = false, length = 64)
    private String maintenanceId;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "object_id", nullable = false, length = 128)
    private String objectId;

    @Column(name = "field_code", nullable = false, length = 128)
    private String fieldCode;

    @Column(name = "label_key", length = 191)
    private String labelKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 16)
    private PolicyFieldDataType dataType;

    @Lob
    @Column(name = "base_value")
    private String baseValue;

    @Lob
    @Column(name = "current_value")
    private String currentValue;

    @Lob
    @Column(name = "proposed_value")
    private String proposedValue;

    @Lob
    @Column(name = "applied_value")
    private String appliedValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_status", nullable = false, length = 16)
    private MaintenanceFieldConflictStatus conflictStatus;

    @Column(name = "resolution_code", length = 64)
    private String resolutionCode;

    @Column(name = "conflict_operation_id", length = 128)
    private String conflictOperationId;

    @Column(name = "conflict_detected_at")
    private LocalDateTime conflictDetectedAt;

    @Column(name = "conflict_policy_version")
    private Long conflictPolicyVersion;

    @Column(name = "conflict_evidence_hash", length = 64)
    private String conflictEvidenceHash;

    @Column(name = "resolution_operation_id", length = 128)
    private String resolutionOperationId;

    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;

    @Column(name = "resolution_evidence_hash", length = 64)
    private String resolutionEvidenceHash;

    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensitivity", length = 16)
    private PolicyFieldSensitivityLevel sensitivity;

    @Enumerated(EnumType.STRING)
    @Column(name = "masking_policy", length = 24)
    private PolicyFieldMaskingPolicy maskingPolicy;

    @Column(name = "change_type_code", length = 64)
    private String changeTypeCode;

    // ==================== 跨案检索冗余键与生效三要素（g05-06）====================

    /**
     * 所属保单 ID（**冗余键**，由案件读模型复制）。
     * <p>
     * 🔴 域内字段差异表的检索主键是案件号，而对外运营要按**保单**跨案查字段级历史
     * （需求文档 §14：「支持按案件号、外部单号、保单号…检索」）⇒ 必须冗余，否则须先查案件表再回查，
     * 且在途案件（案件级投影未完成）会漏。
     * </p>
     */
    @Column(name = "policy_id", length = 64)
    private String policyId;

    /** 所属保单号（冗余键，跨案检索入口，同 {@link #policyId}） */
    @Column(name = "policy_number", length = 64)
    private String policyNumber;

    /**
     * 外部单证号（批单号）：本次变更在 Policy 落定时签发的批改文书号。
     * <p>
     * 来源为 Policy 生效回执 {@code MaintenancePolicyApplicationEvidence.endorsementNo()}，
     * 在生效任务推进时批量回填；未生效的变更恒为 {@code null}（此刻尚无单证事实）。
     * </p>
     */
    @Column(name = "endorsement_no", length = 64)
    private String endorsementNo;

    /**
     * 生效区间起：约定生效日（案件读模型 {@code specific_effective_date} 的冗余副本）。
     * <p>
     * 🔴 与 {@link #businessEffectiveAt} 共同构成「生效区间（起止）」这一检索要素（G05/AC-07）。
     * 二者是**原始事实**的复制，语义归属（是否为「值有效期窗口」）待业务裁示，见 G05 §六 R-14；
     * 裁示若改口径，只需改查询映射、无需改本表结构。
     * </p>
     */
    @Column(name = "specific_effective_date")
    private LocalDateTime specificEffectiveDate;

    /**
     * 生效区间止：业务生效时点及偏移（案件读模型 {@code business_effective_at} 的冗余副本，
     * ISO-8601 字符串形态，含时区偏移）。
     */
    @Column(name = "business_effective_at", length = 40)
    private String businessEffectiveAt;

    /** 费用方向（案件读模型 {@code balance_direction} 的冗余副本：DEBIT 补收 / CREDIT 退费 / NONE 无费用） */
    @Enumerated(EnumType.STRING)
    @Column(name = "fee_direction", length = 16)
    private MaintenanceBalanceDirection feeDirection;
}
