package com.titanium.maintenance.web.response.casecreation;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;

/**
 * 跨案保全历史响应（后台/端上展示，G05/AC-07）。
 *
 * <p>
 * 🔴 三要素（{@code effectiveStart} / {@code effectiveEnd} / {@code feeDirection} /
 * {@code endorsementNo}）为需求文档 §14 明列的检索与展示口径，两端以 ISO-8601 字符串同型对接。
 * </p>
 * <p>
 * 字段级行中的四个值与敏感度/掩码策略成对出现：敏感字段在应用层已按目录策略脱敏，本层不再加工。
 * </p>
 */
public record MaintenanceHistoryVO(
        String caseId,
        String maintenanceNo,
        String policyId,
        String policyNumber,
        MaintenanceType maintenanceType,
        MaintenanceStatus status,
        MaintenanceEffectStatus effectStatus,
        String effectiveStart,
        String effectiveEnd,
        MaintenanceBalanceDirection feeDirection,
        String endorsementNo,
        LocalDateTime createdAt,
        List<FieldChangeHistoryVO> fieldChanges) {

    public MaintenanceHistoryVO {
        fieldChanges = List.copyOf(fieldChanges);
    }

    /** 字段级变更历史行。 */
    public record FieldChangeHistoryVO(
            String itemCode,
            String objectId,
            String fieldCode,
            String labelKey,
            PolicyFieldDataType dataType,
            String baseValue,
            String currentValue,
            String proposedValue,
            String appliedValue,
            MaintenanceFieldConflictStatus conflictStatus,
            String resolutionCode,
            String effectiveStart,
            String effectiveEnd,
            MaintenanceBalanceDirection feeDirection,
            String endorsementNo,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy,
            String changeTypeCode) {
    }
}
