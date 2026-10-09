package com.titanium.maintenance.infrastructure.client.product;

import java.time.LocalDateTime;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.response.paidup.ReducedPaidUpPolicyQueryResponse;

/**
 * 减额缴清净费率策略的保全域 Feign 客户端（g04-05）。
 * <p>
 * 消费产品域 {@code ProductReducedPaidUpPolicyApi}（GET /api/v1/reduced-paid-up-policies/effective），
 * 返回类型为产品域 api 层 {@code ReducedPaidUpPolicyQueryResponse} 强类型（禁 Map/Object）。
 * </p>
 */
@FeignClient(name = "titanium-product-service", contextId = "productReducedPaidUpPolicyApi", path = "/api/v1")
public interface ProductReducedPaidUpPolicyClient {

    @GetMapping("/reduced-paid-up-policies/effective")
    ApiResponse<ReducedPaidUpPolicyQueryResponse> queryEffectiveReducedPaidUpPolicy(
            @RequestParam String productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime businessTime);
}
