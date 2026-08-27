package com.titanium.maintenance.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

class MaintenanceItemDefinitionTest {

    @Test
    void shouldFreezeConfigurationCollections() {
        Set<MaintenanceChannel> channels = new HashSet<>(Set.of(MaintenanceChannel.MANUAL));
        List<MaintenanceFieldRule> fields = new ArrayList<>(
                List.of(MaintenanceFieldRule.editable("policy.contact.mobile", true, false)));

        MaintenanceItemDefinition definition = definition(
                "CONTACT_CHANGE", channels, fields, Set.of(), MaintenanceFeeMode.NONE);
        channels.add(MaintenanceChannel.API);
        fields.clear();

        assertEquals(Set.of(MaintenanceChannel.MANUAL), definition.channels());
        assertEquals(1, definition.fieldRules().size());
        assertTrue(definition.allowsField("policy.contact.mobile"));
        assertFalse(definition.supportsChannel(MaintenanceChannel.API));
    }

    @Test
    void shouldRejectEnabledFeeStepForNoFeeItem() {
        List<MaintenanceStepDefinition> steps = List.of(
                MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                MaintenanceStepDefinition.required(2, MaintenanceStepType.FEE_SETTLEMENT),
                MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT));

        assertThrows(MaintenanceValidationException.class, () -> new MaintenanceItemDefinition(
                "CONTACT_CHANGE", "1.0.0", "联系方式变更", MaintenanceItemCategory.BASIC_INFORMATION,
                Set.of(MaintenanceChannel.MANUAL), List.of(), steps, MaintenanceFeeMode.NONE,
                MaintenanceEffectiveRule.immediate(), Set.of(), true));
    }

    @Test
    void shouldRequireConditionRuleForOptionalFeeStep() {
        assertThrows(MaintenanceValidationException.class, () -> new MaintenanceItemDefinition(
                "COVERAGE_CHANGE", "1.0.0", "保额变更", MaintenanceItemCategory.COVERAGE,
                Set.of(MaintenanceChannel.MANUAL), List.of(), List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.required(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.OPTIONAL,
                new MaintenanceEffectiveRule(Set.of(EffectiveTimeType.IMMEDIATE),
                        EffectiveTimeType.IMMEDIATE, 0, 0),
                Set.of(), true));
    }

    @Test
    void shouldEvaluateCompatibilityFromBothDefinitions() {
        MaintenanceItemDefinition surrender = definition(
                "SURRENDER", Set.of(MaintenanceChannel.MANUAL), List.of(),
                Set.of("CONTACT_CHANGE"), MaintenanceFeeMode.NONE);
        MaintenanceItemDefinition contactChange = definition(
                "CONTACT_CHANGE", Set.of(MaintenanceChannel.MANUAL), List.of(),
                Set.of(), MaintenanceFeeMode.NONE);

        assertFalse(surrender.isCompatibleWith(contactChange));
        assertFalse(contactChange.isCompatibleWith(surrender));
    }

    @Test
    void shouldRejectNullConfigurationEntriesAsBusinessValidation() {
        List<MaintenanceFieldRule> fields = new ArrayList<>();
        fields.add(null);

        assertThrows(MaintenanceValidationException.class, () -> definition(
                "CONTACT_CHANGE", Set.of(MaintenanceChannel.MANUAL), fields,
                Set.of(), MaintenanceFeeMode.NONE));
    }

    private MaintenanceItemDefinition definition(String itemCode, Set<MaintenanceChannel> channels,
            List<MaintenanceFieldRule> fields, Set<String> incompatibleItems, MaintenanceFeeMode feeMode) {
        List<MaintenanceStepDefinition> steps = List.of(
                MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT));
        return new MaintenanceItemDefinition(itemCode, "1.0.0", itemCode,
                MaintenanceItemCategory.BASIC_INFORMATION, channels, fields, steps, feeMode,
                MaintenanceEffectiveRule.immediate(), incompatibleItems, true);
    }
}
