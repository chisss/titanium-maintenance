package com.titanium.maintenance.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropertyMaintenanceLegacyExecutionFeatureAdapterTest {

    @Test
    void shouldEnableLegacyExecutionWhenPropertyIsTrue() {
        var adapter = new PropertyMaintenanceLegacyExecutionFeatureAdapter(true);

        assertTrue(adapter.isEnabled("tenant-1"));
    }

    @Test
    void shouldDisableLegacyExecutionWhenPropertyIsFalse() {
        var adapter = new PropertyMaintenanceLegacyExecutionFeatureAdapter(false);

        assertFalse(adapter.isEnabled("tenant-1"));
    }
}
