package com.titanium.maintenance.infrastructure.adapter;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.infrastructure.client.PaymentPremiumCollectionClient;
import com.titanium.maintenance.port.PaymentPremiumCollectionPort;
import com.titanium.metadata.errorcode.PaymentErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.request.CreatePaymentOrderRequest;
import com.titanium.payment.api.response.PaymentOrderResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Payment 追加保费收款单防腐适配器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentPremiumCollectionAdapter implements PaymentPremiumCollectionPort {

    private final PaymentPremiumCollectionClient client;

    @Override
    public CollectionFact create(CollectionRequest request) {
        CreatePaymentOrderRequest body = new CreatePaymentOrderRequest();
        body.setOrderId(request.paymentOrderId());
        body.setPolicyId(request.policyId());
        body.setCustomerId(request.customerId());
        body.setAmount(request.amount());
        body.setCurrency(request.currency());
        body.setPaymentMethod(request.paymentMethod());
        body.setDescription(request.description());
        try {
            ApiResponse<PaymentOrderResponse> existing = client.get(
                    request.paymentOrderId(), request.tenantId());
            if (existing != null && existing.isSuccess() && existing.getData() != null) {
                return toFact(existing.getData(), request);
            }
            if (!paymentOrderMissing(existing)) {
                throw remoteError("Payment 保全收款单回查失败: " + responseDetail(existing));
            }
            return toFact(requireSuccess(client.create(body, request.tenantId())), request);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("创建 Payment 保全收款单失败, tenantId={}, paymentOrderId={}, errorType={}",
                    request.tenantId(), request.paymentOrderId(), exception.getClass().getSimpleName());
            throw remoteError("Payment 保全收款单创建失败");
        }
    }

    @Override
    public CollectionFact get(String tenantId, String paymentOrderId) {
        try {
            CollectionFact fact = toFact(requireSuccess(client.get(paymentOrderId, tenantId)), null);
            if (!paymentOrderId.equals(fact.paymentOrderId())) {
                throw remoteError("Payment 保全收款单回查结果单号不一致");
            }
            return fact;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("回查 Payment 保全收款单失败, tenantId={}, paymentOrderId={}, errorType={}",
                    tenantId, paymentOrderId, exception.getClass().getSimpleName());
            throw remoteError("Payment 保全收款单回查失败");
        }
    }

    private PaymentOrderResponse requireSuccess(ApiResponse<PaymentOrderResponse> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw remoteError("Payment 保全收款单响应失败: " + responseDetail(response));
        }
        return response.getData();
    }

    private boolean paymentOrderMissing(ApiResponse<PaymentOrderResponse> response) {
        return response != null
                && PaymentErrorCode.PAYMENT_ORDER_NOT_EXIST.getCode().equals(response.getCode());
    }

    private String responseDetail(ApiResponse<PaymentOrderResponse> response) {
        return response == null ? "空响应" : response.getCode() + ":" + response.getMessage();
    }

    private CollectionFact toFact(PaymentOrderResponse response, CollectionRequest request) {
        if (response.getOrderId() == null || response.getStatus() == null
                || response.getAmount() == null || response.getCurrency() == null) {
            throw remoteError("Payment 保全收款单响应字段不完整");
        }
        if (request != null
                && (!Objects.equals(request.paymentOrderId(), response.getOrderId())
                        || !Objects.equals(request.policyId(), response.getPolicyId())
                        || !Objects.equals(request.customerId(), response.getCustomerId())
                        || request.amount().compareTo(response.getAmount()) != 0
                        || !request.currency().equalsIgnoreCase(response.getCurrency()))) {
            throw remoteError("Payment 保全收款单创建结果与请求不一致");
        }
        return new CollectionFact(
                response.getOrderId(), response.getPolicyId(), response.getCustomerId(),
                response.getAmount(), response.getCurrency(), response.getPaymentMethod(),
                response.getStatus(), response.getTransactionId(), response.getPaymentDate());
    }

    private BusinessException remoteError(String message) {
        return new BusinessException(
                message, "MAINTENANCE_PAYMENT_REMOTE_ERROR", HttpStatus.BAD_GATEWAY);
    }
}
