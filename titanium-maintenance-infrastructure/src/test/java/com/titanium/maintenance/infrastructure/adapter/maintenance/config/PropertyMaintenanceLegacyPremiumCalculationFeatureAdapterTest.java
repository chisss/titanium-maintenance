package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropertyMaintenanceLegacyPremiumCalculationFeatureAdapterTest {

    @Test
    void shouldEnableLegacyPremiumCalculationWhenPropertyIsTrue() {
        var adapter = new PropertyMaintenanceLegacyPremiumCalculationFeatureAdapter(true);

        assertTrue(adapter.isEnabled("tenant-1"));
    }

    @Test
    void shouldDisableLegacyPremiumCalculationWhenPropertyIsFalse() {
        var adapter = new PropertyMaintenanceLegacyPremiumCalculationFeatureAdapter(false);

        assertFalse(adapter.isEnabled("tenant-1"));
    }
}
