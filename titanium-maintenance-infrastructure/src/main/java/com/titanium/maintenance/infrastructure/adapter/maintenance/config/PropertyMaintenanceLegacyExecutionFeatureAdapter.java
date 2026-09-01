package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.port.maintenance.MaintenanceLegacyExecutionFeaturePort;

/** 使用部署配置控制旧版整案执行写能力，查询和独立案件任务级生效不受影响。 */
@Component
public class PropertyMaintenanceLegacyExecutionFeatureAdapter implements MaintenanceLegacyExecutionFeaturePort {

    private final boolean enabled;

    public PropertyMaintenanceLegacyExecutionFeatureAdapter(
            @Value("${titanium.maintenance.legacy-execution-enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled(String tenantId) {
        return enabled;
    }
}
