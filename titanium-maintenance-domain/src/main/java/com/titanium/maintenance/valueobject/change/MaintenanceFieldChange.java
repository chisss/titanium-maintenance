package com.titanium.maintenance.valueobject.change;

import java.util.Objects;

import com.titanium.maintenance.common.enums.MaintenanceChangeType;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项对单个 Policy 业务字段的权威变化证据。 */
public record MaintenanceFieldChange(String itemCode, String objectId, String fieldCode,
        MaintenanceFieldValue baseValue, MaintenanceFieldValue currentValue,
        MaintenanceFieldValue proposedValue, MaintenanceFieldValue appliedValue,
        MaintenanceFieldConflictStatus conflictStatus, String resolutionCode) {

    public MaintenanceFieldChange {
        requireText("itemCode", itemCode);
        requireText("objectId", objectId);
        requireText("fieldCode", fieldCode);
        requireValue("baseValue", baseValue);
        requireValue("currentValue", currentValue);
        requireValue("proposedValue", proposedValue);
        requireValue("conflictStatus", conflictStatus);
        validateSameType(baseValue, currentValue, proposedValue, appliedValue);
        if (baseValue.equals(proposedValue)) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldChange", "proposedValue", "拟变更值不能与基准值相同");
        }
        resolutionCode = normalize(resolutionCode);
        if (conflictStatus == MaintenanceFieldConflictStatus.RESOLVED && resolutionCode == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldChange", "resolutionCode", "已解决冲突必须记录解决方式");
        }
        if (conflictStatus != MaintenanceFieldConflictStatus.RESOLVED && resolutionCode != null) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldChange", "resolutionCode", "未解决状态不能记录冲突解决方式");
        }
    }

    /** 根据 Policy 基准值创建字段变更提案。 */
    public static MaintenanceFieldChange propose(String itemCode, String objectId, String fieldCode,
            MaintenanceFieldValue baseValue, MaintenanceFieldValue proposedValue) {
        return new MaintenanceFieldChange(itemCode, objectId, fieldCode, baseValue, baseValue, proposedValue,
                null, MaintenanceFieldConflictStatus.NONE, null);
    }

    /** 刷新执行前当前值并检测顺序外冲突。 */
    public MaintenanceFieldChange refreshCurrent(MaintenanceFieldValue latestValue) {
        requireValue("latestValue", latestValue);
        validateSameType(baseValue, latestValue, proposedValue, appliedValue);
        if (conflictStatus == MaintenanceFieldConflictStatus.RESOLVED && latestValue.equals(currentValue)) {
            return this;
        }
        MaintenanceFieldConflictStatus nextStatus = !latestValue.equals(baseValue)
                && !latestValue.equals(proposedValue)
                        ? MaintenanceFieldConflictStatus.DETECTED
                        : MaintenanceFieldConflictStatus.NONE;
        return new MaintenanceFieldChange(itemCode, objectId, fieldCode, baseValue, latestValue, proposedValue,
                appliedValue, nextStatus, null);
    }

    /** 确认仍采用本案拟变更值解决冲突。 */
    public MaintenanceFieldChange resolveUsingProposed(String resolutionCode) {
        requireDetectedConflict();
        return new MaintenanceFieldChange(itemCode, objectId, fieldCode, baseValue, currentValue, proposedValue,
                appliedValue, MaintenanceFieldConflictStatus.RESOLVED, requireText("resolutionCode", resolutionCode));
    }

    /** 放弃本案字段拟值并采用 Policy 当前值。 */
    public MaintenanceFieldChange resolveUsingCurrent() {
        requireDetectedConflict();
        return new MaintenanceFieldChange(itemCode, objectId, fieldCode, baseValue, currentValue, currentValue,
                appliedValue, MaintenanceFieldConflictStatus.RESOLVED,
                MaintenanceFieldConflictResolutionAction.USE_CURRENT.getCode());
    }

    /** 以重新录入的强类型值替换原拟值。 */
    public MaintenanceFieldChange resolveUsingReentered(MaintenanceFieldValue reenteredValue) {
        requireDetectedConflict();
        requireValue("reenteredValue", reenteredValue);
        validateSameType(baseValue, reenteredValue);
        if (reenteredValue.equals(baseValue) || reenteredValue.equals(currentValue)) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldChange", "reenteredValue", "重新录入值不能等于基准值或当前值");
        }
        return new MaintenanceFieldChange(itemCode, objectId, fieldCode, baseValue, currentValue, reenteredValue,
                appliedValue, MaintenanceFieldConflictStatus.RESOLVED,
                MaintenanceFieldConflictResolutionAction.REENTER.getCode());
    }

    /** 记录 Policy 回执中的实际生效值。 */
    public MaintenanceFieldChange markApplied(MaintenanceFieldValue actualValue) {
        if (conflictStatus == MaintenanceFieldConflictStatus.DETECTED) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldChange", "conflictStatus", "存在未解决冲突时不能记录生效结果");
        }
        requireValue("actualValue", actualValue);
        validateSameType(baseValue, currentValue, proposedValue, actualValue);
        return new MaintenanceFieldChange(itemCode, objectId, fieldCode, baseValue, currentValue, proposedValue,
                actualValue, conflictStatus, resolutionCode);
    }

    public MaintenanceChangeType changeType() {
        if (baseValue.isNull()) {
            return MaintenanceChangeType.ADD;
        }
        if (proposedValue.isNull()) {
            return MaintenanceChangeType.DELETE;
        }
        return MaintenanceChangeType.MODIFY;
    }

    public String key() {
        return objectId + ":" + fieldCode;
    }

    public boolean hasUnresolvedConflict() {
        return conflictStatus == MaintenanceFieldConflictStatus.DETECTED;
    }

    private void requireDetectedConflict() {
        if (conflictStatus != MaintenanceFieldConflictStatus.DETECTED) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldChange", "conflictStatus", "只有待解决冲突才能执行冲突解决");
        }
    }

    private static void validateSameType(MaintenanceFieldValue first, MaintenanceFieldValue... values) {
        for (MaintenanceFieldValue value : values) {
            if (value != null && first.dataType() != value.dataType()) {
                throw new MaintenanceValidationException(
                        "MaintenanceFieldChange", "字段变化的 base/current/proposed/applied 类型必须一致");
            }
        }
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException("MaintenanceFieldChange", fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(String fieldName, T value) {
        if (Objects.isNull(value)) {
            throw new MaintenanceValidationException("MaintenanceFieldChange", fieldName, "字段不能为空");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
