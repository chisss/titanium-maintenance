package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.port.maintenance.MaintenanceConfigurationFeaturePort;

/** 使用部署配置控制保全配置写能力，查询能力不受影响。 */
@Component
public class PropertyMaintenanceConfigurationFeatureAdapter implements MaintenanceConfigurationFeaturePort {

    private final boolean writeEnabled;

    public PropertyMaintenanceConfigurationFeatureAdapter(
            @Value("${titanium.maintenance.configuration.write-enabled:true}") boolean writeEnabled) {
        this.writeEnabled = writeEnabled;
    }

    @Override
    public boolean isWriteEnabled(String tenantId) {
        return writeEnabled;
    }
}
