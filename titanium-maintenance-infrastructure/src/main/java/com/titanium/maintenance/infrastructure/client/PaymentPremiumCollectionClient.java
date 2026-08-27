package com.titanium.maintenance.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.request.CreatePaymentOrderRequest;
import com.titanium.payment.api.response.PaymentOrderResponse;

/** Maintenance 调用 Payment 正式收款单契约的 Feign 客户端。 */
@FeignClient(
        name = "titanium-payment",
        contextId = "maintenancePaymentPremiumCollectionClient",
        path = "/api/v1/payments")
public interface PaymentPremiumCollectionClient {

    @PostMapping
    ApiResponse<PaymentOrderResponse> create(
            @RequestBody CreatePaymentOrderRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId);

    @GetMapping("/{paymentId}")
    ApiResponse<PaymentOrderResponse> get(
            @PathVariable("paymentId") String paymentId,
            @RequestHeader("X-Tenant-Id") String tenantId);
}
