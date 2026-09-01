package com.titanium.maintenance.infrastructure.client.product;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.request.premium.PremiumCalculationRequest;
import com.titanium.product.api.request.premium.PremiumLifecycleAdjustmentRequest;
import com.titanium.product.api.request.premium.PremiumLifecycleReversalRequest;
import com.titanium.product.api.response.premium.PremiumCalculationResponse;
import com.titanium.product.api.response.premium.PremiumLifecycleAdjustmentResponse;

/** Maintenance 调用 Product 生命周期计价契约的 Feign 客户端。 */
@FeignClient(
        name = "titanium-product-service",
        contextId = "maintenanceProductPremiumLifecycleClient",
        path = "/api/v1")
public interface ProductPremiumLifecycleClient {

    @PostMapping("/products/{productId}/premium-calculations")
    ApiResponse<PremiumCalculationResponse> calculate(
            @PathVariable("productId") String productId,
            @RequestBody PremiumCalculationRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId);

    @PostMapping("/premium-calculations/lifecycle-adjustments")
    ApiResponse<PremiumLifecycleAdjustmentResponse> createAdjustment(
            @RequestBody PremiumLifecycleAdjustmentRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId);

    @PostMapping("/premium-lifecycle-adjustments/reversals")
    ApiResponse<PremiumLifecycleAdjustmentResponse> createReversal(
            @RequestBody PremiumLifecycleReversalRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId);
}
