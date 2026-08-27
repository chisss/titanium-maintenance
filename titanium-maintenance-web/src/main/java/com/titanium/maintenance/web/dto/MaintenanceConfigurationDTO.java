package com.titanium.maintenance.web.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenancePremiumRecalculationTiming;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建或替换保全项配置的完整后台入参。 */
public record MaintenanceConfigurationDTO(
        @NotNull @Valid DefinitionDTO definition,
        @NotNull LocalDateTime validFrom,
        LocalDateTime validTo) {

    /** 保全项版本内容。 */
    public record DefinitionDTO(
            @NotBlank @Size(max = 64) String itemCode,
            @NotBlank @Size(max = 64) String version,
            @NotBlank @Size(max = 200) String name,
            @NotNull MaintenanceItemCategory category,
            @NotEmpty Set<MaintenanceChannel> channels,
            @NotNull List<@Valid FieldRuleDTO> fieldRules,
            @NotEmpty List<@Valid StepDTO> steps,
            @NotNull MaintenanceFeeMode feeMode,
            @NotNull @Valid EffectiveRuleDTO effectiveRule,
            @NotNull Set<@NotBlank @Size(max = 64) String> incompatibleItemCodes,
            boolean atomicOnly,
            @NotNull @Valid ControlsDTO controls) {
    }

    /** 可配置字段规则。 */
    public record FieldRuleDTO(
            @NotBlank @Size(max = 200) String fieldCode,
            boolean required,
            boolean visible,
            boolean editable,
            boolean allowClear,
            @Size(max = 128) String conditionRuleCode,
            PolicyFieldValueType expectedValueType) {
    }

    /** 标准流程步骤。 */
    public record StepDTO(
            @Min(1) @Max(100) int sequence,
            @NotNull MaintenanceStepType stepType,
            @NotNull MaintenanceStepMode mode,
            @Size(max = 128) String conditionRuleCode) {
    }

    /** 生效模式及业务日期边界。 */
    public record EffectiveRuleDTO(
            @NotEmpty Set<EffectiveTimeType> allowedModes,
            @NotNull EffectiveTimeType defaultMode,
            @Min(0) @Max(36500) int maxRetroactiveDays,
            @Min(0) @Max(36500) int maxFutureDays) {
    }

    /** 渠道、材料、审批、费用、权限和输出控制。 */
    public record ControlsDTO(
            @NotEmpty Set<@Valid ChannelCapabilityDTO> channelCapabilities,
            @NotNull List<@Valid MaterialRequirementDTO> materialRequirements,
            @NotNull Set<@NotBlank @Size(max = 128) String> crossFieldRuleCodes,
            @Size(max = 128) String approvalPolicyCode,
            @NotNull @Valid FeeRuleDTO feeRule,
            @NotNull @Valid AccessRuleDTO accessRule,
            @NotNull @Valid OutputRuleDTO outputRule) {
    }

    /** 单渠道审核能力。 */
    public record ChannelCapabilityDTO(
            @NotNull MaintenanceChannel channel,
            boolean autoApprovalAllowed) {
    }

    /** 材料要求。 */
    public record MaterialRequirementDTO(
            @NotBlank @Size(max = 128) String materialCode,
            boolean required,
            @Size(max = 128) String conditionRuleCode) {
    }

    /** 收退费公式、结算门禁和重算时点。 */
    public record FeeRuleDTO(
            @Size(max = 128) String formulaCode,
            @Size(max = 128) String settlementGateRuleCode,
            @NotNull MaintenancePremiumRecalculationTiming recalculationTiming) {
    }

    /** 配置实例所需的操作与查看权限。 */
    public record AccessRuleDTO(
            @NotNull Set<@NotBlank @Size(max = 128) String> operationPermissionCodes,
            @NotNull Set<@NotBlank @Size(max = 128) String> viewPermissionCodes) {
    }

    /** 凭证、通知和归档模板。 */
    public record OutputRuleDTO(
            @Size(max = 128) String voucherTemplateCode,
            @NotNull Set<@NotBlank @Size(max = 128) String> notificationTemplateCodes,
            @Size(max = 128) String archiveTemplateCode) {
    }
}
