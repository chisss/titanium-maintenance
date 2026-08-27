package com.titanium.maintenance.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.titanium.maintenance.infrastructure.adapter.UnavailableProductMaintenanceOfferingAdapter;
import com.titanium.maintenance.port.ProductMaintenanceOfferingPort;

/** Product 保全 Offering Adapter 配置。 */
@Configuration
public class ProductMaintenanceOfferingAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(ProductMaintenanceOfferingPort.class)
    ProductMaintenanceOfferingPort productMaintenanceOfferingPort() {
        return new UnavailableProductMaintenanceOfferingAdapter();
    }
}
