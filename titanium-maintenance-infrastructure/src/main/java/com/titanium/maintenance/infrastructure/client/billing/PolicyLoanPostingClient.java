package com.titanium.maintenance.infrastructure.client.billing;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.billing.api.request.loan.CreatePolicyLoanPostingRequest;
import com.titanium.billing.api.response.loan.PolicyLoanPostingResponse;
import com.titanium.metadata.response.ApiResponse;

/**
 * 保单贷款入账的保全域 Feign 客户端（g04-03）。
 * <p>
 * 消费计费域 {@code PolicyLoanPostingApi}（POST /api/v1/policy-loan-postings），入参为计费域 api 层
 * {@code CreatePolicyLoanPostingRequest}、返回类型为 {@code PolicyLoanPostingResponse} 强类型（禁 Map/Object）。
 * </p>
 */
@FeignClient(name = "titanium-billing", contextId = "policyLoanPostingApi", path = "/api/v1/policy-loan-postings")
public interface PolicyLoanPostingClient {

    @PostMapping
    ApiResponse<PolicyLoanPostingResponse> post(@RequestBody CreatePolicyLoanPostingRequest request);
}
