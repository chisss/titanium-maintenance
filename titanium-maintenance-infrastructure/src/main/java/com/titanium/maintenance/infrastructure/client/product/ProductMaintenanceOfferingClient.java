package com.titanium.maintenance.infrastructure.client.product;

import java.time.OffsetDateTime;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.response.maintenance.ProductMaintenanceOfferingResolutionResponse;

/** Maintenance 调用 Product 正式保全 Offering API 的 Feign 客户端。 */
@FeignClient(
        name = "titanium-product-service",
        contextId = "maintenanceProductOfferingClient",
        path = "/api/v1/products")
public interface ProductMaintenanceOfferingClient {

    @GetMapping("/{productId}/maintenance-offering")
    ApiResponse<ProductMaintenanceOfferingResolutionResponse> resolve(
            @PathVariable("productId") String productId,
            @RequestParam("productVersion") String productVersion,
            @RequestParam("planVersion") String planVersion,
            @RequestParam("policyStatus") String policyStatus,
            @RequestParam("source") String source,
            @RequestParam("businessEffectiveAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime businessEffectiveAt,
            @RequestHeader("X-Tenant-Id") String tenantId);
}
