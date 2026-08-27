package com.titanium.maintenance.configuration;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;

/**
 * 版本化保全项定义。
 *
 * <p>该对象封装配置内在一致性；产品适用性和外部规则执行由应用层通过 Port 编排。</p>
 */
public record MaintenanceItemDefinition(String itemCode, String version, String name,
        MaintenanceItemCategory category, Set<MaintenanceChannel> channels,
        List<MaintenanceFieldRule> fieldRules, List<MaintenanceStepDefinition> steps,
        MaintenanceFeeMode feeMode, MaintenanceEffectiveRule effectiveRule,
        Set<String> incompatibleItemCodes, boolean atomicOnly, MaintenanceItemControls controls) {

    private static final Pattern ITEM_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    public MaintenanceItemDefinition {
        itemCode = validateItemCode(itemCode);
        version = requireText("version", version);
        name = requireText("name", name);
        category = requireValue("category", category);
        channels = immutableNonEmptySet("channels", channels);
        fieldRules = immutableList("fieldRules", fieldRules);
        steps = sortedSteps(steps);
        feeMode = requireValue("feeMode", feeMode);
        effectiveRule = requireValue("effectiveRule", effectiveRule);
        incompatibleItemCodes = immutableTextSet("incompatibleItemCodes", incompatibleItemCodes);
        controls = controls == null ? MaintenanceItemControls.defaults(channels) : controls;
        validateUniqueFieldRules(fieldRules);
        validateWorkflow(steps, feeMode);
        validateIncompatibleItems(itemCode, incompatibleItemCodes);
        validateControlChannels(channels, controls);
    }

    /** 兼容 Phase 1 定义结构的辅助构造器。 */
    public MaintenanceItemDefinition(String itemCode, String version, String name,
            MaintenanceItemCategory category, Set<MaintenanceChannel> channels,
            List<MaintenanceFieldRule> fieldRules, List<MaintenanceStepDefinition> steps,
            MaintenanceFeeMode feeMode, MaintenanceEffectiveRule effectiveRule,
            Set<String> incompatibleItemCodes, boolean atomicOnly) {
        this(itemCode, version, name, category, channels, fieldRules, steps, feeMode, effectiveRule,
                incompatibleItemCodes, atomicOnly, null);
    }

    /** 判断渠道是否已在当前配置版本中开放。 */
    public boolean supportsChannel(MaintenanceChannel channel) {
        return channels.contains(channel);
    }

    /** 判断字段是否在当前配置版本中允许修改。 */
    public boolean allowsField(String fieldCode) {
        return fieldRules.stream().anyMatch(rule -> rule.editable() && rule.fieldCode().equals(fieldCode));
    }

    /** 判断两个保全项定义是否允许同案选择。 */
    public boolean isCompatibleWith(MaintenanceItemDefinition other) {
        return other != null
                && !incompatibleItemCodes.contains(other.itemCode)
                && !other.incompatibleItemCodes.contains(itemCode);
    }

    /** 校验定义是否具备进入审批流程的内部完整性。 */
    public void validateForSubmission() {
        controls.validateForSubmission(channels, feeMode);
    }

    /** 复制当前内容并创建新版本定义。 */
    public MaintenanceItemDefinition reviseTo(String newVersion) {
        return new MaintenanceItemDefinition(itemCode, newVersion, name, category, channels, fieldRules,
                steps, feeMode, effectiveRule, incompatibleItemCodes, atomicOnly, controls);
    }

    private static void validateControlChannels(
            Set<MaintenanceChannel> channels, MaintenanceItemControls controls) {
        if (!controls.channels().equals(channels)) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemDefinition", "controls", "控制配置渠道必须与保全项受理渠道一致");
        }
    }

    private static void validateUniqueFieldRules(List<MaintenanceFieldRule> fieldRules) {
        Set<String> fieldCodes = new HashSet<>();
        for (MaintenanceFieldRule rule : fieldRules) {
            if (!fieldCodes.add(rule.fieldCode())) {
                throw new MaintenanceValidationException(
                        "MaintenanceItemDefinition", "fieldRules", "保全项字段编码不能重复: " + rule.fieldCode());
            }
        }
    }

    private static void validateWorkflow(List<MaintenanceStepDefinition> steps, MaintenanceFeeMode feeMode) {
        Map<MaintenanceStepType, MaintenanceStepDefinition> byType = uniqueStepsByType(steps);
        if (!byType.containsKey(MaintenanceStepType.EFFECT)
                || byType.get(MaintenanceStepType.EFFECT).mode() == MaintenanceStepMode.SKIPPED) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemDefinition", "steps", "保全项必须包含可执行的生效步骤");
        }
        MaintenanceStepDefinition feeStep = byType.get(MaintenanceStepType.FEE_SETTLEMENT);
        if (feeMode == MaintenanceFeeMode.NONE
                && feeStep != null && feeStep.mode() != MaintenanceStepMode.SKIPPED) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemDefinition", "steps", "无费用保全项不能启用收退费步骤");
        }
        if (feeMode == MaintenanceFeeMode.REQUIRED
                && (feeStep == null || feeStep.mode() == MaintenanceStepMode.SKIPPED)) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemDefinition", "steps", "必须收退费的保全项需要启用收退费步骤");
        }
        if (feeMode == MaintenanceFeeMode.OPTIONAL
                && (feeStep == null || feeStep.mode() != MaintenanceStepMode.CONDITIONAL)) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemDefinition", "steps", "条件收退费的保全项必须配置条件收退费步骤");
        }
    }

    private static Map<MaintenanceStepType, MaintenanceStepDefinition> uniqueStepsByType(
            List<MaintenanceStepDefinition> steps) {
        Set<Integer> sequences = new HashSet<>();
        for (MaintenanceStepDefinition step : steps) {
            if (!sequences.add(step.sequence())) {
                throw new MaintenanceValidationException(
                        "MaintenanceItemDefinition", "steps", "保全步骤顺序不能重复: " + step.sequence());
            }
        }
        try {
            return steps.stream().collect(Collectors.toMap(MaintenanceStepDefinition::stepType, Function.identity()));
        } catch (IllegalStateException exception) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemDefinition", "steps", "同一保全项不能重复配置相同步骤");
        }
    }

    private static void validateIncompatibleItems(String itemCode, Set<String> incompatibleItemCodes) {
        if (incompatibleItemCodes.contains(itemCode)) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemDefinition", "incompatibleItemCodes", "保全项不能与自身互斥");
        }
        if (incompatibleItemCodes.stream().anyMatch(code -> !ITEM_CODE_PATTERN.matcher(code).matches())) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemDefinition", "incompatibleItemCodes", "互斥保全项编码格式非法");
        }
    }

    private static List<MaintenanceStepDefinition> sortedSteps(List<MaintenanceStepDefinition> values) {
        if (values == null || values.isEmpty()) {
            throw new MaintenanceValidationException("MaintenanceItemDefinition", "steps", "至少配置一个保全步骤");
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new MaintenanceValidationException("MaintenanceItemDefinition", "steps", "保全步骤不能包含空项");
        }
        return values.stream()
                .sorted(Comparator.comparingInt(MaintenanceStepDefinition::sequence))
                .toList();
    }

    private static String validateItemCode(String value) {
        String itemCode = requireText("itemCode", value);
        if (!ITEM_CODE_PATTERN.matcher(itemCode).matches()) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemDefinition", "itemCode", "保全项编码必须使用大写字母、数字和下划线");
        }
        return itemCode;
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException("MaintenanceItemDefinition", fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(String fieldName, T value) {
        if (value == null) {
            throw new MaintenanceValidationException("MaintenanceItemDefinition", fieldName, "字段不能为空");
        }
        return value;
    }

    private static <T> Set<T> immutableNonEmptySet(String fieldName, Set<T> values) {
        if (values == null || values.isEmpty()) {
            throw new MaintenanceValidationException("MaintenanceItemDefinition", fieldName, "集合不能为空");
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new MaintenanceValidationException("MaintenanceItemDefinition", fieldName, "集合不能包含空项");
        }
        return Set.copyOf(values);
    }

    private static <T> List<T> immutableList(String fieldName, List<T> values) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new MaintenanceValidationException("MaintenanceItemDefinition", fieldName, "集合不能包含空项");
        }
        return List.copyOf(values);
    }

    private static Set<String> immutableTextSet(String fieldName, Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new MaintenanceValidationException("MaintenanceItemDefinition", fieldName, "集合不能包含空编码");
        }
        return Set.copyOf(values);
    }
}
