package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.config.MaintenanceFeatureProperties;

class PropertyMaintenanceLegacyExecutionFeatureAdapterTest {

    @Test
    void shouldEnableLegacyExecutionWhenPropertyIsTrue() {
        var adapter = new PropertyMaintenanceLegacyExecutionFeatureAdapter(featureProperties(true));

        assertTrue(adapter.isEnabled());
    }

    @Test
    void shouldDisableLegacyExecutionWhenPropertyIsFalse() {
        var adapter = new PropertyMaintenanceLegacyExecutionFeatureAdapter(featureProperties(false));

        assertFalse(adapter.isEnabled());
    }

    /** 构造开关配置（m22-04：@Value → @ConfigurationProperties 后测试改传配置对象） */
    private static MaintenanceFeatureProperties featureProperties(boolean enabled) {
        MaintenanceFeatureProperties properties = new MaintenanceFeatureProperties();
        properties.setLegacyCreationEnabled(enabled);
        properties.setLegacyExecutionEnabled(enabled);
        properties.setLegacyPremiumCalculationEnabled(enabled);
        return properties;
    }
}
