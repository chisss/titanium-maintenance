package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.config.MaintenanceFeatureProperties;
import com.titanium.maintenance.port.maintenance.MaintenanceLegacyExecutionFeaturePort;

/** 使用部署配置控制旧版整案执行写能力，查询和独立案件任务级生效不受影响。 */
@Component
public class PropertyMaintenanceLegacyExecutionFeatureAdapter implements MaintenanceLegacyExecutionFeaturePort {

    private final boolean enabled;

    public PropertyMaintenanceLegacyExecutionFeatureAdapter(
            MaintenanceFeatureProperties properties) {
        this.enabled = properties.isLegacyExecutionEnabled();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
