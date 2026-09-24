package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.config.MaintenanceFeatureProperties;

class PropertyMaintenanceConfigurationFeatureAdapterTest {

    @Test
    void shouldEnableConfigurationWritesWhenPropertyIsTrue() {
        var adapter = new PropertyMaintenanceConfigurationFeatureAdapter(configProperties(true));

        assertTrue(adapter.isWriteEnabled());
    }

    @Test
    void shouldDisableConfigurationWritesWhenPropertyIsFalse() {
        var adapter = new PropertyMaintenanceConfigurationFeatureAdapter(configProperties(false));

        assertFalse(adapter.isWriteEnabled());
    }

    /** 构造 configuration 子域写开关（同上层改造） */
    private static MaintenanceFeatureProperties configProperties(boolean writeEnabled) {
        MaintenanceFeatureProperties properties = new MaintenanceFeatureProperties();
        properties.getConfiguration().setWriteEnabled(writeEnabled);
        return properties;
    }
}
