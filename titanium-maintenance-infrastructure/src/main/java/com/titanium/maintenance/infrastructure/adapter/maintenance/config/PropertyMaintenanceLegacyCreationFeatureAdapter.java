package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.config.MaintenanceFeatureProperties;
import com.titanium.maintenance.port.maintenance.MaintenanceLegacyCreationFeaturePort;

/** 使用部署配置控制旧版保全建案能力，旧版查询和案件后续操作不受影响。 */
@Component
public class PropertyMaintenanceLegacyCreationFeatureAdapter implements MaintenanceLegacyCreationFeaturePort {

    private final boolean enabled;

    public PropertyMaintenanceLegacyCreationFeatureAdapter(
            MaintenanceFeatureProperties properties) {
        this.enabled = properties.isLegacyCreationEnabled();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
