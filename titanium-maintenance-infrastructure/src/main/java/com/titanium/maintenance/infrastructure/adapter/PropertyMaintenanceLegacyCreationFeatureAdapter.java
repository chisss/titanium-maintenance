package com.titanium.maintenance.infrastructure.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.port.MaintenanceLegacyCreationFeaturePort;

/** 使用部署配置控制旧版保全建案能力，旧版查询和案件后续操作不受影响。 */
@Component
public class PropertyMaintenanceLegacyCreationFeatureAdapter implements MaintenanceLegacyCreationFeaturePort {

    private final boolean enabled;

    public PropertyMaintenanceLegacyCreationFeatureAdapter(
            @Value("${titanium.maintenance.legacy-creation-enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled(String tenantId) {
        return enabled;
    }
}
