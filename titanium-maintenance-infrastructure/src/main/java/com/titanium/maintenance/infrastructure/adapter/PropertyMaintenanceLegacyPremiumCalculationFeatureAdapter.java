package com.titanium.maintenance.infrastructure.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.port.MaintenanceLegacyPremiumCalculationFeaturePort;

/** 使用部署配置控制旧版人工保费金额写入能力，查询和新任务级收退费流程不受影响。 */
@Component
public class PropertyMaintenanceLegacyPremiumCalculationFeatureAdapter
        implements MaintenanceLegacyPremiumCalculationFeaturePort {

    private final boolean enabled;

    public PropertyMaintenanceLegacyPremiumCalculationFeatureAdapter(
            @Value("${titanium.maintenance.legacy-premium-calculation-enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled(String tenantId) {
        return enabled;
    }
}
