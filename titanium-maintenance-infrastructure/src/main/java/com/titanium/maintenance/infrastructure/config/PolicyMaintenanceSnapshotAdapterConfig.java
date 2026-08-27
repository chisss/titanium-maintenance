package com.titanium.maintenance.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.titanium.maintenance.infrastructure.adapter.UnavailablePolicyMaintenanceSnapshotAdapter;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort;

/** Policy 建案快照 Adapter 配置。 */
@Configuration
public class PolicyMaintenanceSnapshotAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(PolicyMaintenanceSnapshotPort.class)
    PolicyMaintenanceSnapshotPort policyMaintenanceSnapshotPort() {
        return new UnavailablePolicyMaintenanceSnapshotAdapter();
    }
}
