package com.titanium.maintenance.infrastructure.client.policy;

import java.time.LocalDate;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;

/** Maintenance 调用 Policy 字段目录正式 API 的 Feign Client。 */
@FeignClient(
        name = "titanium-policy",
        contextId = "maintenancePolicyFieldCatalogClient",
        path = "/api/v1/policy-field-catalogs")
public interface PolicyFieldCatalogClient {

    @GetMapping("/current")
    ApiResponse<PolicyFieldCatalogResponse> getCurrentCatalog(
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String policyType,
            @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate businessDate);
}
