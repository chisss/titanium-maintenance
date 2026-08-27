package com.titanium.maintenance.infrastructure.adapter;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.infrastructure.client.PaymentMaintenanceRefundClient;
import com.titanium.maintenance.port.PaymentMaintenanceRefundPort;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.request.CreateRefundOrderRequest;
import com.titanium.payment.api.response.RefundOrderResponse;

import lombok.RequiredArgsConstructor;

/** Payment 独立退款单防腐适配器。 */
@Component
@RequiredArgsConstructor
public class PaymentMaintenanceRefundAdapter implements PaymentMaintenanceRefundPort {

    private final PaymentMaintenanceRefundClient client;

    @Override
    public RefundFact create(RefundRequest request) {
        CreateRefundOrderRequest body = new CreateRefundOrderRequest();
        body.setRefundRequestId(request.refundRequestId());
        body.setSourcePostingId(request.sourcePostingId());
        body.setOriginalPaymentId(request.originalPaymentId());
        body.setAmount(request.amount());
        body.setCurrency(request.currency());
        body.setReason(request.reason());
        body.setRequestedBy(request.requestedBy());
        RefundOrderResponse response = requireSuccess(client.create(body, request.tenantId()));
        RefundFact fact = toFact(response);
        if (!Objects.equals(request.refundRequestId(), fact.refundRequestId())
                || !Objects.equals(request.sourcePostingId(), fact.sourcePostingId())
                || !Objects.equals(request.originalPaymentId(), fact.originalPaymentId())
                || request.amount().compareTo(fact.amount()) != 0
                || !request.currency().equalsIgnoreCase(fact.currency())) {
            throw remoteError("Payment 退款单创建结果与撤销补偿请求不一致");
        }
        return fact;
    }

    @Override
    public RefundFact get(String tenantId, String refundOrderId) {
        RefundFact fact = toFact(requireSuccess(client.get(refundOrderId, tenantId)));
        if (!refundOrderId.equals(fact.refundOrderId())) {
            throw remoteError("Payment 退款单回查结果单号不一致");
        }
        return fact;
    }

    private RefundOrderResponse requireSuccess(ApiResponse<RefundOrderResponse> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String detail = response == null ? "空响应" : response.getCode() + ":" + response.getMessage();
            throw remoteError("Payment 退款单响应失败: " + detail);
        }
        return response.getData();
    }

    private RefundFact toFact(RefundOrderResponse response) {
        if (response.getRefundOrderId() == null || response.getRefundRequestId() == null
                || response.getSourcePostingId() == null || response.getOriginalPaymentId() == null
                || response.getAmount() == null || response.getCurrency() == null || response.getStatus() == null) {
            throw remoteError("Payment 退款单响应字段不完整");
        }
        return new RefundFact(
                response.getRefundOrderId(), response.getRefundRequestId(), response.getSourcePostingId(),
                response.getOriginalPaymentId(), response.getAmount(), response.getCurrency(), response.getStatus(),
                response.getFailureCode(), response.getFailureMessage(), response.getUpdatedAt());
    }

    private BusinessException remoteError(String message) {
        return new BusinessException(message, "MAINTENANCE_PAYMENT_REFUND_REMOTE_ERROR", HttpStatus.BAD_GATEWAY);
    }
}
