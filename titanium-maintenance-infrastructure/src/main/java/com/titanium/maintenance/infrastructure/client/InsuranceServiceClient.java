package com.titanium.maintenance.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.InsuranceResponse;

/** Maintenance 查询 Policy 投保单溯源事实的 Feign 客户端。 */
@FeignClient(name = "titanium-policy", contextId = "maintenanceInsuranceClient", path = "/api/v1/insurances")
public interface InsuranceServiceClient {

    @GetMapping("/{insuranceId}")
    ApiResponse<InsuranceResponse> getInsurance(
            @PathVariable("insuranceId") String insuranceId,
            @RequestHeader("X-Tenant-Id") String tenantId);
}
