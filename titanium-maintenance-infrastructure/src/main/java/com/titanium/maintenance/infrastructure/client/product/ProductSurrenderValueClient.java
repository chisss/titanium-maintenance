package com.titanium.maintenance.infrastructure.client.product;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.request.premium.SurrenderValueCalculationRequest;
import com.titanium.product.api.response.premium.SurrenderValueCalculationResponse;

/** Maintenance 调用 Product 退保价值契约的 Feign 客户端。 */
@FeignClient(name = "titanium-product-service", contextId = "maintenanceSurrenderValueApi", path = "/api/v1")
public interface ProductSurrenderValueClient {

    @PostMapping("/premium-calculations/surrender-values")
    ApiResponse<SurrenderValueCalculationResponse> calculate(
            @RequestBody SurrenderValueCalculationRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId);
}
