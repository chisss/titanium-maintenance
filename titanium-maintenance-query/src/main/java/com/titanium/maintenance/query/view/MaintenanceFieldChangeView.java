package com.titanium.maintenance.query.view;

import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
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
}
