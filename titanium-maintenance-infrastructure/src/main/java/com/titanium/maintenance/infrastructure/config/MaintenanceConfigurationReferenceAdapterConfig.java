package com.titanium.maintenance.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.titanium.maintenance.infrastructure.adapter.UnavailableMaintenanceConfigurationReferenceAdapter;
import com.titanium.maintenance.port.MaintenanceConfigurationReferencePort;

/** 保全配置外部引用端口的失败关闭后备配置。 */
@Configuration(proxyBeanMethods = false)
public class MaintenanceConfigurationReferenceAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(MaintenanceConfigurationReferencePort.class)
    MaintenanceConfigurationReferencePort maintenanceConfigurationReferencePort() {
        return new UnavailableMaintenanceConfigurationReferenceAdapter();
    }
}
