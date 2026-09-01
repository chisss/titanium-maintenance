package com.titanium.maintenance.infrastructure.client.claim;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.claim.api.response.ClaimResponse;
import com.titanium.metadata.response.ApiResponse;

/** Claim 理赔权威查询客户端。 */
@FeignClient(name = "titanium-claim", contextId = "maintenanceClaimImpactClient", path = "/api/v1/claims")
public interface ClaimRetroactiveImpactClient {

    @GetMapping("/policy/{policyId}")
    ApiResponse<List<ClaimResponse>> getClaimsByPolicyId(
            @PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId);
}
