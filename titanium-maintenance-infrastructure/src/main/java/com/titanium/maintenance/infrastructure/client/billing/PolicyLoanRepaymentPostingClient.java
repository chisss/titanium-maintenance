package com.titanium.maintenance.infrastructure.client.billing;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.billing.api.request.loan.CreatePolicyLoanRepaymentPostingRequest;
import com.titanium.billing.api.response.loan.PolicyLoanRepaymentPostingResponse;
import com.titanium.metadata.response.ApiResponse;

/**
 * 保单贷款还款入账的保全域 Feign 客户端（g04-04）。
 * <p>
 * 消费计费域 {@code PolicyLoanRepaymentPostingApi}（POST {@code /api/v1/policy-loan-repayment-postings}），
 * 入参为计费域 api 层 {@code CreatePolicyLoanRepaymentPostingRequest}、返回类型为
 * {@code PolicyLoanRepaymentPostingResponse} 强类型（禁 Map/Object）。
 * </p>
 */
@FeignClient(name = "titanium-billing", contextId = "policyLoanRepaymentPostingApi",
        path = "/api/v1/policy-loan-repayment-postings")
public interface PolicyLoanRepaymentPostingClient {

    @PostMapping
    ApiResponse<PolicyLoanRepaymentPostingResponse> post(
            @RequestBody CreatePolicyLoanRepaymentPostingRequest request);
}
