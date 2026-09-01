package com.titanium.maintenance.infrastructure.client.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.request.CreateRefundOrderRequest;
import com.titanium.payment.api.response.RefundOrderResponse;

/** Maintenance 调用 Payment 独立退款单契约的 Feign 客户端。 */
@FeignClient(
        name = "titanium-payment",
        contextId = "maintenancePaymentRefundClient",
        path = "/api/v1/refund-orders")
public interface PaymentMaintenanceRefundClient {

    @PostMapping
    ApiResponse<RefundOrderResponse> create(
            @RequestBody CreateRefundOrderRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId);

    @GetMapping("/{refundOrderId}")
    ApiResponse<RefundOrderResponse> get(
            @PathVariable("refundOrderId") String refundOrderId,
            @RequestHeader("X-Tenant-Id") String tenantId);
}
