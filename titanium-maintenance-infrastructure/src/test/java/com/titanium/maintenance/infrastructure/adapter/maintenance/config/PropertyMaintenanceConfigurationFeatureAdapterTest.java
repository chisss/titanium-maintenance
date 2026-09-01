package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropertyMaintenanceConfigurationFeatureAdapterTest {

    @Test
    void shouldEnableConfigurationWritesWhenPropertyIsTrue() {
        var adapter = new PropertyMaintenanceConfigurationFeatureAdapter(true);

        assertTrue(adapter.isWriteEnabled("tenant-1"));
    }

    @Test
    void shouldDisableConfigurationWritesWhenPropertyIsFalse() {
        var adapter = new PropertyMaintenanceConfigurationFeatureAdapter(false);

        assertFalse(adapter.isWriteEnabled("tenant-1"));
    }
}
