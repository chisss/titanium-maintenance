package com.titanium.maintenance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;

import com.titanium.billing.api.RetroactivePeriodAdjustmentApi;
import com.titanium.product.api.ProductMaintenancePremiumQuoteApi;
import com.titanium.product.api.ProductPremiumCalculationApi;
import com.titanium.underwriting.api.MaintenanceUnderwritingApi;

/**
 * 外域正式 API Feign 装配回归测试，防止适配器因客户端未注册导致服务无法启动。
 */
class MaintenanceExternalFeignConfigurationTest {

    @Test
    void shouldRegisterMaintenanceExternalApis() {
        EnableFeignClients annotation = MaintenanceExternalFeignConfiguration.class
                .getAnnotation(EnableFeignClients.class);

        assertTrue(Arrays.asList(annotation.clients()).contains(MaintenanceUnderwritingApi.class));
        assertTrue(Arrays.asList(annotation.clients()).contains(ProductMaintenancePremiumQuoteApi.class));
        assertTrue(Arrays.asList(annotation.clients()).contains(ProductPremiumCalculationApi.class));
        assertTrue(Arrays.asList(annotation.clients()).contains(RetroactivePeriodAdjustmentApi.class));
    }
}
