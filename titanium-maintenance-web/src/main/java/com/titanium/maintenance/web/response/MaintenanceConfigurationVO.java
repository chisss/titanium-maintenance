package com.titanium.maintenance.web.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceConfigurationAction;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceFieldValidationType;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.common.enums.config.MaintenancePremiumRecalculationTiming;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

/** 后台配置详情；不包含数据库 JSON 或保单实例数据。 */
public record MaintenanceConfigurationVO(
        String configurationId,
        String revisionOfConfigurationId,
        MaintenanceItemConfigurationStatus status,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String contentHash,
        Long rowVersion,
        boolean sensitiveDetailsVisible,
        DefinitionVO definition,
        PublicationEvidenceVO publicationEvidence,
        List<LifecycleAuditVO> lifecycleAudits) {

    public record DefinitionVO(
            String itemCode,
            String version,
            String name,
            MaintenanceItemCategory category,
            Set<MaintenanceChannel> channels,
            List<FieldRuleVO> fieldRules,
            List<StepVO> steps,
            MaintenanceFeeMode feeMode,
            EffectiveRuleVO effectiveRule,
            Set<String> incompatibleItemCodes,
            boolean atomicOnly,
            ControlsVO controls) {
    }

    /** 无敏感查看权限时，条件规则和期望值类型被移除并标记。 */
    public record FieldRuleVO(
            String fieldCode,
            boolean required,
            boolean visible,
            boolean editable,
            boolean allowClear,
            String conditionRuleCode,
            PolicyFieldValueType expectedValueType,
            MaintenanceFieldValidationType validationType,
            String validationPattern,
            String validationMessage,
            boolean detailsRedacted) {
    }

    public record StepVO(
            int sequence,
            MaintenanceStepType stepType,
            MaintenanceStepMode mode,
            String conditionRuleCode) {
    }

    public record EffectiveRuleVO(
            Set<EffectiveTimeType> allowedModes,
            EffectiveTimeType defaultMode,
            int maxRetroactiveDays,
            int maxFutureDays) {
    }

    public record ControlsVO(
            Set<ChannelCapabilityVO> channelCapabilities,
            List<MaterialRequirementVO> materialRequirements,
            Set<String> crossFieldRuleCodes,
            String approvalPolicyCode,
            FeeRuleVO feeRule,
            AccessRuleVO accessRule,
            OutputRuleVO outputRule) {
    }

    public record ChannelCapabilityVO(MaintenanceChannel channel, boolean autoApprovalAllowed) {
    }

    public record MaterialRequirementVO(
            String materialCode, boolean required, String conditionRuleCode) {
    }

    public record FeeRuleVO(
            String formulaCode,
            String settlementGateRuleCode,
            MaintenancePremiumRecalculationTiming recalculationTiming) {
    }

    public record AccessRuleVO(Set<String> operationPermissionCodes, Set<String> viewPermissionCodes) {
    }

    public record OutputRuleVO(
            String voucherTemplateCode,
            Set<String> notificationTemplateCodes,
            String archiveTemplateCode) {
    }

    public record PublicationEvidenceVO(
            String catalogVersion, String catalogHash, LocalDateTime validatedAt) {
    }

    public record LifecycleAuditVO(
            MaintenanceConfigurationAction action,
            String operatorId,
            LocalDateTime occurredAt,
            String detail) {
    }
}
