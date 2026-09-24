package com.titanium.maintenance.infrastructure.client.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.request.refund.CreateRefundOrderRequest;
import com.titanium.payment.api.response.refund.RefundOrderResponse;

/** Maintenance 调用 Payment 独立退款单契约的 Feign 客户端。 */
@FeignClient(
        name = "titanium-payment-service",
        contextId = "maintenancePaymentRefundClient",
        path = "/api/v1/refund-orders")
public interface PaymentMaintenanceRefundClient {

    @PostMapping
    ApiResponse<RefundOrderResponse> create(
            @RequestBody CreateRefundOrderRequest request);

    @GetMapping("/{refundOrderId}")
    ApiResponse<RefundOrderResponse> get(
            @PathVariable String refundOrderId);
}
