package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropertyMaintenanceLegacyExecutionFeatureAdapterTest {

    @Test
    void shouldEnableLegacyExecutionWhenPropertyIsTrue() {
        var adapter = new PropertyMaintenanceLegacyExecutionFeatureAdapter(true);

        assertTrue(adapter.isEnabled());
    }

    @Test
    void shouldDisableLegacyExecutionWhenPropertyIsFalse() {
        var adapter = new PropertyMaintenanceLegacyExecutionFeatureAdapter(false);

        assertFalse(adapter.isEnabled());
    }
}
