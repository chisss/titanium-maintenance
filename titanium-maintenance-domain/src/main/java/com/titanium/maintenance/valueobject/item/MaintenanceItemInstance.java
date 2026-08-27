package com.titanium.maintenance.valueobject.item;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;

/** 创建案件时冻结的保全项配置与字段提案。 */
public record MaintenanceItemInstance(String itemCode, String configVersion, String name,
        MaintenanceItemCategory category, Set<MaintenanceChannel> channels,
        List<MaintenanceFieldRule> fieldRules, List<MaintenanceStepDefinition> steps,
        MaintenanceFeeMode feeMode, MaintenanceEffectiveRule effectiveRule,
        Set<String> incompatibleItemCodes, boolean atomicOnly,
        MaintenanceItemControls controls,
        MaintenanceItemSelectionEvidence selectionEvidence,
        List<MaintenanceFieldChange> fieldChanges, LocalDateTime selectedAt) {

    public MaintenanceItemInstance {
        if (itemCode == null || itemCode.isBlank() || configVersion == null || configVersion.isBlank()
                || name == null || name.isBlank()) {
            throw new MaintenanceValidationException("MaintenanceItemInstance", "保全项标识、版本和名称不能为空");
        }
        if (category == null || channels == null || channels.isEmpty() || feeMode == null
                || effectiveRule == null || selectedAt == null) {
            throw new MaintenanceValidationException("MaintenanceItemInstance", "保全项配置快照字段不完整");
        }
        channels = Set.copyOf(channels);
        fieldRules = fieldRules == null ? List.of() : List.copyOf(fieldRules);
        steps = steps == null ? List.of() : List.copyOf(steps);
        incompatibleItemCodes = incompatibleItemCodes == null ? Set.of() : Set.copyOf(incompatibleItemCodes);
        controls = controls == null ? MaintenanceItemControls.defaults(channels) : controls;
        selectionEvidence = selectionEvidence == null
                ? MaintenanceItemSelectionEvidence.legacy(configVersion)
                : selectionEvidence;
        fieldChanges = fieldChanges == null ? List.of() : List.copyOf(fieldChanges);
        new MaintenanceItemDefinition(itemCode, configVersion, name, category, channels, fieldRules, steps,
                feeMode, effectiveRule, incompatibleItemCodes, atomicOnly, controls);
        if (!configVersion.equals(selectionEvidence.configurationVersion())) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemInstance", "selectionEvidence", "项目定义版本与冻结证据版本不一致");
        }
        validateChanges(itemCode, fieldRules, fieldChanges);
    }

    /** 从已校验的定义创建案件级配置快照。 */
    public static MaintenanceItemInstance from(MaintenanceItemDefinition definition, LocalDateTime selectedAt) {
        if (definition == null) {
            throw new MaintenanceValidationException("MaintenanceItemInstance", "definition", "保全项定义不能为空");
        }
        return new MaintenanceItemInstance(definition.itemCode(), definition.version(), definition.name(),
                definition.category(), definition.channels(), definition.fieldRules(), definition.steps(),
                definition.feeMode(), definition.effectiveRule(), definition.incompatibleItemCodes(),
                definition.atomicOnly(), definition.controls(), null, List.of(), selectedAt);
    }

    /** 从已校验的定义和权威选择证据创建案件级配置快照。 */
    public static MaintenanceItemInstance from(
            MaintenanceItemDefinition definition,
            MaintenanceItemSelectionEvidence selectionEvidence,
            LocalDateTime selectedAt) {
        if (definition == null || selectionEvidence == null || !selectionEvidence.authoritative()) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemInstance", "selectionEvidence", "独立建案必须冻结权威项目选择证据");
        }
        return new MaintenanceItemInstance(definition.itemCode(), definition.version(), definition.name(),
                definition.category(), definition.channels(), definition.fieldRules(), definition.steps(),
                definition.feeMode(), definition.effectiveRule(), definition.incompatibleItemCodes(),
                definition.atomicOnly(), definition.controls(), selectionEvidence, List.of(), selectedAt);
    }

    /** 使用完整字段提案替换当前草稿，旧事件仍保留每次修改历史。 */
    public MaintenanceItemInstance withFieldChanges(List<MaintenanceFieldChange> changes) {
        return new MaintenanceItemInstance(itemCode, configVersion, name, category, channels, fieldRules, steps,
                feeMode, effectiveRule, incompatibleItemCodes, atomicOnly, controls,
                selectionEvidence, changes, selectedAt);
    }

    /** 判断两个案件内保全项是否允许并案。 */
    public boolean isCompatibleWith(MaintenanceItemInstance other) {
        return other != null
                && !atomicOnly
                && !other.atomicOnly
                && !incompatibleItemCodes.contains(other.itemCode)
                && !other.incompatibleItemCodes.contains(itemCode);
    }

    /** 判断重复加项命令是否携带完全相同的冻结配置和证据。 */
    public boolean sameFrozenSelection(
            MaintenanceItemDefinition definition,
            MaintenanceItemSelectionEvidence evidence) {
        return definition != null
                && evidence != null
                && itemCode.equals(definition.itemCode())
                && configVersion.equals(definition.version())
                && name.equals(definition.name())
                && category == definition.category()
                && channels.equals(definition.channels())
                && fieldRules.equals(definition.fieldRules())
                && steps.equals(definition.steps())
                && feeMode == definition.feeMode()
                && effectiveRule.equals(definition.effectiveRule())
                && incompatibleItemCodes.equals(definition.incompatibleItemCodes())
                && atomicOnly == definition.atomicOnly()
                && controls.equals(definition.controls())
                && selectionEvidence.sameAuthoritativeSelection(evidence);
    }

    public boolean hasUnresolvedConflicts() {
        return fieldChanges.stream().anyMatch(MaintenanceFieldChange::hasUnresolvedConflict);
    }

    private static void validateChanges(String itemCode, List<MaintenanceFieldRule> fieldRules,
            List<MaintenanceFieldChange> changes) {
        Map<String, MaintenanceFieldRule> ruleByCode = fieldRules.stream()
                .collect(Collectors.toMap(MaintenanceFieldRule::fieldCode, Function.identity()));
        Set<String> changeKeys = new HashSet<>();
        for (MaintenanceFieldChange change : changes) {
            if (!itemCode.equals(change.itemCode())) {
                throw new MaintenanceValidationException(
                        "MaintenanceItemInstance", "fieldChanges", "字段变化不属于当前保全项");
            }
            MaintenanceFieldRule rule = ruleByCode.get(change.fieldCode());
            if (rule == null || !rule.editable()) {
                throw new MaintenanceValidationException(
                        "MaintenanceItemInstance", "fieldChanges", "字段不在可编辑白名单: " + change.fieldCode());
            }
            if (!rule.allowClear() && change.proposedValue().isNull()) {
                throw new MaintenanceValidationException(
                        "MaintenanceItemInstance", "fieldChanges", "字段不允许清空: " + change.fieldCode());
            }
            if (!changeKeys.add(change.key())) {
                throw new MaintenanceValidationException(
                        "MaintenanceItemInstance", "fieldChanges", "同一业务对象字段不能重复提交: " + change.key());
            }
        }
    }
}
