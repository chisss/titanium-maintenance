package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.config.MaintenanceFeatureProperties;
import com.titanium.maintenance.port.maintenance.MaintenanceConfigurationFeaturePort;

/** 使用部署配置控制保全配置写能力，查询能力不受影响。 */
@Component
public class PropertyMaintenanceConfigurationFeatureAdapter implements MaintenanceConfigurationFeaturePort {

    private final boolean writeEnabled;

    public PropertyMaintenanceConfigurationFeatureAdapter(
            MaintenanceFeatureProperties properties) {
        this.writeEnabled = properties.getConfiguration().isWriteEnabled();
    }

    @Override
    public boolean isWriteEnabled() {
        return writeEnabled;
    }
}
