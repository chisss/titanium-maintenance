package com.titanium.maintenance.infrastructure.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.billing.api.response.BillResponse;
import com.titanium.metadata.response.ApiResponse;

/** Billing 账单权威查询客户端。 */
@FeignClient(name = "titanium-billing", contextId = "maintenanceBillingImpactClient", path = "/api/bills")
public interface BillingRetroactiveImpactClient {

    @GetMapping("/policy/{policyId}")
    ApiResponse<List<BillResponse>> getBillsByPolicyId(
            @PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-ID") String tenantId);
}
