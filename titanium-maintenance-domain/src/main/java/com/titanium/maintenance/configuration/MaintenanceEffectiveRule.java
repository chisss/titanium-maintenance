package com.titanium.maintenance.configuration;

import java.time.LocalDateTime;
import java.util.Set;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项允许的生效模式及日期边界。 */
public record MaintenanceEffectiveRule(Set<EffectiveTimeType> allowedModes, EffectiveTimeType defaultMode,
        int maxRetroactiveDays, int maxFutureDays) {

    public MaintenanceEffectiveRule {
        if (allowedModes == null || allowedModes.isEmpty()) {
            throw new MaintenanceValidationException("MaintenanceEffectiveRule", "allowedModes", "至少配置一种生效模式");
        }
        allowedModes = Set.copyOf(allowedModes);
        if (defaultMode == null || !allowedModes.contains(defaultMode)) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectiveRule", "defaultMode", "默认生效模式必须包含在允许模式中");
        }
        if (maxRetroactiveDays < 0 || maxFutureDays < 0) {
            throw new MaintenanceValidationException("MaintenanceEffectiveRule", "生效日期边界不能为负数");
        }
        if (allowedModes.contains(EffectiveTimeType.RETROACTIVE) && maxRetroactiveDays == 0) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectiveRule", "maxRetroactiveDays", "允许追溯时必须配置追溯上限");
        }
        if ((allowedModes.contains(EffectiveTimeType.FUTURE)
                || allowedModes.contains(EffectiveTimeType.SPECIFIED_DATE)) && maxFutureDays == 0) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectiveRule", "maxFutureDays", "允许未来或指定日生效时必须配置未来日期上限");
        }
    }

    /** 校验调用方选择的生效模式。 */
    public void validateMode(EffectiveTimeType mode) {
        if (mode == null || !allowedModes.contains(mode)) {
            throw new MaintenanceValidationException("MaintenanceEffectiveRule", "mode", "保全项不支持该生效模式");
        }
    }

    /** 使用租户业务时钟校验调用方选择的日期边界。 */
    public void validateEffectiveDate(
            EffectiveTimeType mode,
            LocalDateTime selectedEffectiveAt,
            LocalDateTime policyEffectiveAt,
            LocalDateTime referenceAt) {
        validateMode(mode);
        if (mode == EffectiveTimeType.RETROACTIVE) {
            requireDateContext(selectedEffectiveAt, policyEffectiveAt, referenceAt);
            if (!selectedEffectiveAt.isBefore(referenceAt)) {
                throw invalid("selectedEffectiveAt", "追溯生效时间必须早于当前租户业务时间");
            }
            if (selectedEffectiveAt.isBefore(policyEffectiveAt)) {
                throw invalid("selectedEffectiveAt", "追溯生效时间不能早于保单起期");
            }
            if (selectedEffectiveAt.toLocalDate()
                    .isBefore(referenceAt.toLocalDate().minusDays(maxRetroactiveDays))) {
                throw invalid("selectedEffectiveAt", "追溯生效时间超过配置允许的追溯天数");
            }
            return;
        }
        if (mode == EffectiveTimeType.FUTURE || mode == EffectiveTimeType.SPECIFIED_DATE) {
            requireDateContext(selectedEffectiveAt, policyEffectiveAt, referenceAt);
            if (!selectedEffectiveAt.isAfter(referenceAt)) {
                throw invalid("selectedEffectiveAt", "未来或指定日生效时间必须晚于当前租户业务时间");
            }
            if (selectedEffectiveAt.toLocalDate()
                    .isAfter(referenceAt.toLocalDate().plusDays(maxFutureDays))) {
                throw invalid("selectedEffectiveAt", "未来或指定日生效时间超过配置允许的未来天数");
            }
            return;
        }
        if (selectedEffectiveAt != null) {
            throw invalid("selectedEffectiveAt", "当前生效模式不能提供指定生效时间");
        }
    }

    /** 创建仅支持立即生效的规则。 */
    public static MaintenanceEffectiveRule immediate() {
        return new MaintenanceEffectiveRule(Set.of(EffectiveTimeType.IMMEDIATE), EffectiveTimeType.IMMEDIATE, 0, 0);
    }

    private void requireDateContext(
            LocalDateTime selectedEffectiveAt,
            LocalDateTime policyEffectiveAt,
            LocalDateTime referenceAt) {
        if (selectedEffectiveAt == null || policyEffectiveAt == null || referenceAt == null) {
            throw invalid("selectedEffectiveAt", "日期型生效模式缺少生效时间、保单起期或租户业务时间");
        }
    }

    private MaintenanceValidationException invalid(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceEffectiveRule", fieldName, message);
    }
}
