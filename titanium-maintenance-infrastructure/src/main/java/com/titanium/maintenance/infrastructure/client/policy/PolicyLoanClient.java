package com.titanium.maintenance.infrastructure.client.policy;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.policy.DisbursePolicyLoanRequest;
import com.titanium.policy.api.request.policy.RepayPolicyLoanRequest;
import com.titanium.policy.api.response.policy.DisbursePolicyLoanResponse;
import com.titanium.policy.api.response.policy.RepayPolicyLoanResponse;

/**
 * 保单贷款放款与还款的保全域 Feign 客户端（g04-03 放款；g04-04 还款）。
 * <p>
 * 消费保单域 {@code PolicyLoanApi}（POST {@code /api/v1/loans/disburse|repay}），入参为保单域 api 层
 * {@code DisbursePolicyLoanRequest}/{@code RepayPolicyLoanRequest}、返回类型为对应 {@code XxxResponse}
 * 强类型（禁 Map/Object）。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "policyLoanApi", path = "/api/v1/loans")
public interface PolicyLoanClient {

    @PostMapping("/disburse")
    ApiResponse<DisbursePolicyLoanResponse> disburse(@RequestBody DisbursePolicyLoanRequest request);

    @PostMapping("/repay")
    ApiResponse<RepayPolicyLoanResponse> repay(@RequestBody RepayPolicyLoanRequest request);
}
