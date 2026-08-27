package com.titanium.maintenance.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropertyMaintenanceLegacyCreationFeatureAdapterTest {

    @Test
    void shouldEnableLegacyCreationWhenPropertyIsTrue() {
        var adapter = new PropertyMaintenanceLegacyCreationFeatureAdapter(true);

        assertTrue(adapter.isEnabled("tenant-1"));
    }

    @Test
    void shouldDisableLegacyCreationWhenPropertyIsFalse() {
        var adapter = new PropertyMaintenanceLegacyCreationFeatureAdapter(false);

        assertFalse(adapter.isEnabled("tenant-1"));
    }
}
