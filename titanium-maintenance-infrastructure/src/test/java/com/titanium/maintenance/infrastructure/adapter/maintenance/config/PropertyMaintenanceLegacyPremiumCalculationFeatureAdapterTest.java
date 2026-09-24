package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.config.MaintenanceFeatureProperties;

class PropertyMaintenanceLegacyPremiumCalculationFeatureAdapterTest {

    @Test
    void shouldEnableLegacyPremiumCalculationWhenPropertyIsTrue() {
        var adapter = new PropertyMaintenanceLegacyPremiumCalculationFeatureAdapter(featureProperties(true));

        assertTrue(adapter.isEnabled());
    }

    @Test
    void shouldDisableLegacyPremiumCalculationWhenPropertyIsFalse() {
        var adapter = new PropertyMaintenanceLegacyPremiumCalculationFeatureAdapter(featureProperties(false));

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
