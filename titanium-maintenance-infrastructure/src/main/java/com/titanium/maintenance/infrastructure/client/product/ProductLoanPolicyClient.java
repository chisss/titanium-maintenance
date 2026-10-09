package com.titanium.maintenance.infrastructure.client.product;

import java.time.LocalDateTime;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.response.loan.LoanPolicyQueryResponse;

/**
 * 保单贷款策略的保全域 Feign 客户端（g04-03）。
 * <p>
 * 消费产品域 {@code ProductLoanPolicyApi}（GET /api/v1/loan-policies/effective），返回类型为产品域 api 层
 * {@code LoanPolicyQueryResponse} 强类型（禁 Map/Object）。
 * </p>
 */
@FeignClient(name = "titanium-product-service", contextId = "productLoanPolicyApi", path = "/api/v1")
public interface ProductLoanPolicyClient {

    @GetMapping("/loan-policies/effective")
    ApiResponse<LoanPolicyQueryResponse> queryEffectiveLoanPolicy(@RequestParam String productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime businessTime);
}
