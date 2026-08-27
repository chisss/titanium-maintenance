package com.titanium.maintenance.infrastructure.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.response.PaymentOrderResponse;

/** Payment 收退款权威查询客户端。 */
@FeignClient(name = "titanium-payment", contextId = "maintenancePaymentImpactClient", path = "/api/v1/payments")
public interface PaymentRetroactiveImpactClient {

    @GetMapping("/business/{businessId}")
    ApiResponse<List<PaymentOrderResponse>> getPaymentOrdersByBusinessId(
            @PathVariable("businessId") String businessId,
            @RequestHeader("X-Tenant-Id") String tenantId);
}
