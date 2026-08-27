package com.titanium.maintenance.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

class MaintenanceEffectiveRuleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 25, 12, 0);
    private static final LocalDateTime POLICY_EFFECTIVE_AT = NOW.minusYears(1);

    @Test
    void shouldAllowRetroactiveDateWithinConfiguredAndPolicyBoundaries() {
        MaintenanceEffectiveRule rule = new MaintenanceEffectiveRule(
                Set.of(EffectiveTimeType.RETROACTIVE), EffectiveTimeType.RETROACTIVE, 30, 0);

        assertDoesNotThrow(() -> rule.validateEffectiveDate(
                EffectiveTimeType.RETROACTIVE, NOW.minusDays(10), POLICY_EFFECTIVE_AT, NOW));
        assertDoesNotThrow(() -> rule.validateEffectiveDate(
                EffectiveTimeType.RETROACTIVE, NOW.minusDays(30).toLocalDate().atStartOfDay(),
                POLICY_EFFECTIVE_AT, NOW));
    }

    @Test
    void shouldRejectRetroactiveDateOutsideConfiguredOrPolicyBoundaries() {
        MaintenanceEffectiveRule rule = new MaintenanceEffectiveRule(
                Set.of(EffectiveTimeType.RETROACTIVE), EffectiveTimeType.RETROACTIVE, 30, 0);

        assertThrows(MaintenanceValidationException.class, () -> rule.validateEffectiveDate(
                EffectiveTimeType.RETROACTIVE, NOW.minusDays(31), POLICY_EFFECTIVE_AT, NOW));
        assertThrows(MaintenanceValidationException.class, () -> rule.validateEffectiveDate(
                EffectiveTimeType.RETROACTIVE, POLICY_EFFECTIVE_AT.minusSeconds(1), POLICY_EFFECTIVE_AT, NOW));
        assertThrows(MaintenanceValidationException.class, () -> rule.validateEffectiveDate(
                EffectiveTimeType.RETROACTIVE, NOW, POLICY_EFFECTIVE_AT, NOW));
    }

    @Test
    void shouldEnforceConfiguredFutureBoundary() {
        MaintenanceEffectiveRule rule = new MaintenanceEffectiveRule(
                Set.of(EffectiveTimeType.FUTURE), EffectiveTimeType.FUTURE, 0, 30);

        assertDoesNotThrow(() -> rule.validateEffectiveDate(
                EffectiveTimeType.FUTURE, NOW.plusDays(10), POLICY_EFFECTIVE_AT, NOW));
        assertThrows(MaintenanceValidationException.class, () -> rule.validateEffectiveDate(
                EffectiveTimeType.FUTURE, NOW.plusDays(31), POLICY_EFFECTIVE_AT, NOW));
    }

    @Test
    void shouldRejectModeOutsideFrozenConfiguration() {
        assertThrows(MaintenanceValidationException.class, () -> MaintenanceEffectiveRule.immediate()
                .validateEffectiveDate(EffectiveTimeType.RETROACTIVE, NOW.minusDays(1), POLICY_EFFECTIVE_AT, NOW));
    }
}
