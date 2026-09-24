package com.titanium.maintenance.infrastructure.client.payment;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.response.payment.PaymentOrderResponse;

/** Payment 收退款权威查询客户端。 */
@FeignClient(name = "titanium-payment-service", contextId = "maintenancePaymentImpactClient", path = "/api/v1/payments")
public interface PaymentRetroactiveImpactClient {

    @GetMapping("/business/{businessId}")
    ApiResponse<List<PaymentOrderResponse>> getPaymentOrdersByBusinessId(
            @PathVariable String businessId);
}
