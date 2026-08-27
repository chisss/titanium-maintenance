package com.titanium.maintenance.configuration.control;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项的渠道、材料、审批、费用、权限与输出控制。 */
public record MaintenanceItemControls(Set<MaintenanceChannelCapability> channelCapabilities,
        List<MaintenanceMaterialRequirement> materialRequirements, Set<String> crossFieldRuleCodes,
        String approvalPolicyCode, MaintenanceFeeRule feeRule, MaintenanceAccessRule accessRule,
        MaintenanceOutputRule outputRule) {

    public MaintenanceItemControls {
        channelCapabilities = immutableNonEmptySet("channelCapabilities", channelCapabilities);
        materialRequirements = immutableList(materialRequirements);
        crossFieldRuleCodes = immutableTextSet("crossFieldRuleCodes", crossFieldRuleCodes);
        approvalPolicyCode = normalize(approvalPolicyCode);
        feeRule = feeRule == null ? MaintenanceFeeRule.none() : feeRule;
        accessRule = accessRule == null ? MaintenanceAccessRule.empty() : accessRule;
        outputRule = outputRule == null ? MaintenanceOutputRule.empty() : outputRule;
        validateUniqueChannels(channelCapabilities);
        validateUniqueMaterials(materialRequirements);
    }

    /** 为 Phase 1 定义创建兼容的未补全控制配置。 */
    public static MaintenanceItemControls defaults(Set<MaintenanceChannel> channels) {
        Set<MaintenanceChannelCapability> capabilities = channels.stream()
                .map(MaintenanceChannelCapability::manualApproval)
                .collect(Collectors.toUnmodifiableSet());
        return new MaintenanceItemControls(capabilities, List.of(), Set.of(), null,
                MaintenanceFeeRule.none(), MaintenanceAccessRule.empty(), MaintenanceOutputRule.empty());
    }

    /** 返回控制配置覆盖的受理渠道。 */
    public Set<MaintenanceChannel> channels() {
        return channelCapabilities.stream()
                .map(MaintenanceChannelCapability::channel)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 校验配置是否具备送审所需的内部完整性。 */
    public void validateForSubmission(Set<MaintenanceChannel> configuredChannels, MaintenanceFeeMode feeMode) {
        if (!channels().equals(configuredChannels)) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemControls", "channelCapabilities", "渠道能力必须完整覆盖保全项受理渠道");
        }
        if (approvalPolicyCode == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemControls", "approvalPolicyCode", "送审前必须配置审批策略引用");
        }
        feeRule.validateFor(feeMode);
        accessRule.validateForSubmission();
    }

    private static void validateUniqueChannels(Set<MaintenanceChannelCapability> capabilities) {
        Set<MaintenanceChannel> channels = new HashSet<>();
        for (MaintenanceChannelCapability capability : capabilities) {
            if (!channels.add(capability.channel())) {
                throw new MaintenanceValidationException(
                        "MaintenanceItemControls", "channelCapabilities", "同一渠道不能重复配置能力");
            }
        }
    }

    private static void validateUniqueMaterials(List<MaintenanceMaterialRequirement> requirements) {
        Set<String> materialCodes = new HashSet<>();
        for (MaintenanceMaterialRequirement requirement : requirements) {
            if (!materialCodes.add(requirement.materialCode())) {
                throw new MaintenanceValidationException(
                        "MaintenanceItemControls", "materialRequirements", "材料编码不能重复: "
                                + requirement.materialCode());
            }
        }
    }

    private static <T> Set<T> immutableNonEmptySet(String fieldName, Set<T> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemControls", fieldName, "集合不能为空且不能包含空项");
        }
        return Set.copyOf(values);
    }

    private static List<MaintenanceMaterialRequirement> immutableList(
            List<MaintenanceMaterialRequirement> values) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemControls", "materialRequirements", "材料清单不能包含空项");
        }
        return List.copyOf(values);
    }

    private static Set<String> immutableTextSet(String fieldName, Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemControls", fieldName, "集合不能包含空编码");
        }
        return values.stream().map(String::trim).collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
