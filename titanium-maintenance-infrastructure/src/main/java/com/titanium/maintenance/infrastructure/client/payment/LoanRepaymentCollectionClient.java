package com.titanium.maintenance.infrastructure.client.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.request.payment.CreatePaymentOrderRequest;
import com.titanium.payment.api.response.payment.PaymentOrderResponse;

/**
 * 保单贷款还款收款的保全域 Feign 客户端（g04-04）。
 * <p>
 * 消费支付域 {@code PaymentApi}（{@code /api/v1/payments}）：还款是<b>向客户收款</b>，方向与保费收取一致，
 * 故复用既有支付单契约而不另建收款契约；入参为支付域 api 层 {@code CreatePaymentOrderRequest}、
 * 返回类型为 {@code PaymentOrderResponse} 强类型（禁 Map/Object）。
 * </p>
 * <p>
 * 🔴 与 {@link PaymentPremiumCollectionClient} <b>同 name 同 path，必须各配唯一 contextId</b>
 * （Feign 的 bean 名由 contextId 派生，重复即启动期冲突）。
 * </p>
 */
@FeignClient(
        name = "titanium-payment-service",
        contextId = "maintenanceLoanRepaymentCollectionClient",
        path = "/api/v1/payments")
public interface LoanRepaymentCollectionClient {

    @PostMapping
    ApiResponse<PaymentOrderResponse> create(
            @RequestBody CreatePaymentOrderRequest request);

    @GetMapping("/{paymentId}")
    ApiResponse<PaymentOrderResponse> get(
            @PathVariable String paymentId);
}
