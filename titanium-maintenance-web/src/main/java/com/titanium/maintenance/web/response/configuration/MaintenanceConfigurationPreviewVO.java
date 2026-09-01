package com.titanium.maintenance.web.response.configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceFieldValidationType;
import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

/** 后台表单与流程的只读预览，不执行条件规则。 */
public record MaintenanceConfigurationPreviewVO(
        String configurationId,
        String itemCode,
        String configurationVersion,
        String name,
        MaintenanceItemConfigurationStatus status,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        Set<MaintenanceChannel> channels,
        List<FieldVO> fields,
        List<StepVO> steps,
        MaintenanceFeeMode feeMode,
        EffectiveRuleVO effectiveRule,
        boolean atomicOnly,
        boolean authoritativeConditionsEvaluated) {

    public record FieldVO(
            String fieldCode,
            boolean required,
            boolean visible,
            boolean editable,
            boolean allowClear,
            PolicyFieldValueType expectedValueType,
            MaintenanceFieldValidationType validationType,
            String validationMessage,
            boolean detailsRedacted) {
    }

    public record StepVO(
            int sequence,
            MaintenanceStepType stepType,
            MaintenanceStepMode mode,
            boolean conditional) {
    }

    public record EffectiveRuleVO(
            Set<EffectiveTimeType> allowedModes,
            EffectiveTimeType defaultMode,
            int maxRetroactiveDays,
            int maxFutureDays) {
    }
}
