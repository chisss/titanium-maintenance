package com.titanium.maintenance.configuration;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;

import com.titanium.maintenance.configuration.control.MaintenanceAccessRule;
import com.titanium.maintenance.configuration.control.MaintenanceFeeRule;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.configuration.control.MaintenanceMaterialRequirement;
import com.titanium.maintenance.configuration.control.MaintenanceOutputRule;

/** 将保全项配置规范化后生成内容证据。 */
final class MaintenanceItemConfigurationHasher {

    private MaintenanceItemConfigurationHasher() {
    }

    static String hash(MaintenanceItemDefinition definition, LocalDateTime validFrom, LocalDateTime validTo) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("definition", definitionContent(definition));
        content.put("validFrom", validFrom.toString());
        content.put("validTo", nullable(validTo));
        return sha256(JSON.toJSONBytes(content));
    }

    private static Map<String, Object> definitionContent(MaintenanceItemDefinition definition) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("itemCode", definition.itemCode());
        content.put("version", definition.version());
        content.put("name", definition.name());
        content.put("category", definition.category().name());
        content.put("channels", sortedNames(definition.channels()));
        content.put("fieldRules", fieldRules(definition));
        content.put("steps", steps(definition));
        content.put("feeMode", definition.feeMode().name());
        content.put("effectiveRule", effectiveRule(definition));
        content.put("incompatibleItemCodes", definition.incompatibleItemCodes().stream().sorted().toList());
        content.put("atomicOnly", definition.atomicOnly());
        content.put("controls", controls(definition.controls()));
        return content;
    }

    private static List<Map<String, Object>> fieldRules(MaintenanceItemDefinition definition) {
        return definition.fieldRules().stream()
                .sorted(Comparator.comparing(MaintenanceFieldRule::fieldCode))
                .map(rule -> {
                    Map<String, Object> content = new LinkedHashMap<>();
                    content.put("fieldCode", rule.fieldCode());
                    content.put("required", rule.required());
                    content.put("visible", rule.visible());
                    content.put("editable", rule.editable());
                    content.put("allowClear", rule.allowClear());
                    content.put("conditionRuleCode", nullable(rule.conditionRuleCode()));
                    content.put("expectedValueType", nullable(rule.expectedValueType()));
                    return content;
                })
                .toList();
    }

    private static List<Map<String, Object>> steps(MaintenanceItemDefinition definition) {
        return definition.steps().stream()
                .sorted(Comparator.comparingInt(MaintenanceStepDefinition::sequence))
                .map(step -> {
                    Map<String, Object> content = new LinkedHashMap<>();
                    content.put("sequence", step.sequence());
                    content.put("stepType", step.stepType().name());
                    content.put("mode", step.mode().name());
                    content.put("conditionRuleCode", nullable(step.conditionRuleCode()));
                    return content;
                })
                .toList();
    }

    private static Map<String, Object> effectiveRule(MaintenanceItemDefinition definition) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("allowedModes", sortedNames(definition.effectiveRule().allowedModes()));
        content.put("defaultMode", definition.effectiveRule().defaultMode().name());
        content.put("maxRetroactiveDays", definition.effectiveRule().maxRetroactiveDays());
        content.put("maxFutureDays", definition.effectiveRule().maxFutureDays());
        return content;
    }

    private static Map<String, Object> controls(MaintenanceItemControls controls) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("channelCapabilities", controls.channelCapabilities().stream()
                .sorted(Comparator.comparing(capability -> capability.channel().name()))
                .map(capability -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("channel", capability.channel().name());
                    value.put("autoApprovalAllowed", capability.autoApprovalAllowed());
                    return value;
                })
                .toList());
        content.put("materialRequirements", controls.materialRequirements().stream()
                .sorted(Comparator.comparing(MaintenanceMaterialRequirement::materialCode))
                .map(requirement -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("materialCode", requirement.materialCode());
                    value.put("required", requirement.required());
                    value.put("conditionRuleCode", nullable(requirement.conditionRuleCode()));
                    return value;
                })
                .toList());
        content.put("crossFieldRuleCodes", controls.crossFieldRuleCodes().stream().sorted().toList());
        content.put("approvalPolicyCode", nullable(controls.approvalPolicyCode()));
        content.put("feeRule", feeRule(controls.feeRule()));
        content.put("accessRule", accessRule(controls.accessRule()));
        content.put("outputRule", outputRule(controls.outputRule()));
        return content;
    }

    private static Map<String, Object> feeRule(MaintenanceFeeRule rule) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("formulaCode", nullable(rule.formulaCode()));
        content.put("settlementGateRuleCode", nullable(rule.settlementGateRuleCode()));
        content.put("recalculationTiming", rule.recalculationTiming().name());
        return content;
    }

    private static Map<String, Object> accessRule(MaintenanceAccessRule rule) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("operationPermissionCodes", rule.operationPermissionCodes().stream().sorted().toList());
        content.put("viewPermissionCodes", rule.viewPermissionCodes().stream().sorted().toList());
        return content;
    }

    private static Map<String, Object> outputRule(MaintenanceOutputRule rule) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("voucherTemplateCode", nullable(rule.voucherTemplateCode()));
        content.put("notificationTemplateCodes", rule.notificationTemplateCodes().stream().sorted().toList());
        content.put("archiveTemplateCode", nullable(rule.archiveTemplateCode()));
        return content;
    }

    private static List<String> sortedNames(Iterable<? extends Enum<?>> values) {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        values.forEach(value -> names.add(value.name()));
        return names.stream().sorted().toList();
    }

    private static String nullable(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
