package com.titanium.maintenance.infrastructure.client.policy;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.policy.PolicyFinancialResponse;

/**
 * 保单财务快照的保全域 Feign 客户端（g04-02）。
 * <p>
 * 消费保单域 {@code PolicyFinancialApi}（GET /api/v1/policies/{policyId}/financial-snapshot），
 * 返回类型为保单域 api 层 {@code PolicyFinancialResponse} 强类型（禁 Map/Object）。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "policyFinancialApi", path = "/api/v1/policies")
public interface PolicyFinancialClient {

    @GetMapping("/{policyId}/financial-snapshot")
    ApiResponse<PolicyFinancialResponse> getFinancialSnapshot(@PathVariable String policyId);
}
