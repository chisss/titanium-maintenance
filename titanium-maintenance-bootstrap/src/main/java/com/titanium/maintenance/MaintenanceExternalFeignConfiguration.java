package com.titanium.maintenance;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

import com.titanium.billing.api.RetroactivePeriodAdjustmentApi;
import com.titanium.product.api.ProductMaintenancePremiumQuoteApi;
import com.titanium.product.api.ProductPremiumCalculationApi;
import com.titanium.underwriting.api.MaintenanceUnderwritingApi;

/**
 * 保全域调用的外域正式 API Feign 装配。
 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {
        MaintenanceUnderwritingApi.class,
        ProductMaintenancePremiumQuoteApi.class,
        ProductPremiumCalculationApi.class,
        RetroactivePeriodAdjustmentApi.class
})
class MaintenanceExternalFeignConfiguration {
}
