package com.titanium.maintenance.infrastructure.client.billing;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.billing.api.request.retroactive.PostPremiumLifecycleAdjustmentRequest;
import com.titanium.billing.api.request.retroactive.ReversePremiumLifecyclePostingRequest;
import com.titanium.billing.api.response.retroactive.PremiumLifecyclePostingResponse;
import com.titanium.billing.api.response.retroactive.PremiumLifecycleReversalResponse;
import com.titanium.metadata.response.ApiResponse;

/** Maintenance 调用 Billing 生命周期余额登记契约的 Feign 客户端。 */
@FeignClient(
        name = "titanium-billing",
        contextId = "premiumLifecyclePostingApi",
        path = "/api/premium-lifecycle-postings")
public interface BillingPremiumLifecycleClient {

    @PostMapping
    ApiResponse<PremiumLifecyclePostingResponse> post(
            @RequestBody PostPremiumLifecycleAdjustmentRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId);

    @PostMapping("/{postingId}/reversals")
    ApiResponse<PremiumLifecycleReversalResponse> reverse(
            @PathVariable("postingId") String postingId,
            @RequestBody ReversePremiumLifecyclePostingRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId);
}
