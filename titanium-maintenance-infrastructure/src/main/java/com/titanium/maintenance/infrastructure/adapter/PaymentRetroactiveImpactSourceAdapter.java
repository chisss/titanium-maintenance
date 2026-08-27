package com.titanium.maintenance.infrastructure.adapter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.infrastructure.client.PaymentRetroactiveImpactClient;
import com.titanium.maintenance.port.MaintenanceRetroactiveImpactSourcePort;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.response.PaymentOrderResponse;

import lombok.RequiredArgsConstructor;

/** Payment 收款与退款追溯影响取证适配器。 */
@Component
@RequiredArgsConstructor
public class PaymentRetroactiveImpactSourceAdapter
        extends AbstractRetroactiveImpactSourceAdapter<PaymentOrderResponse> {

    private static final String ITEM_EVIDENCE_VERSION = "PAYMENT_ORDER_V1";

    private final PaymentRetroactiveImpactClient client;

    @Override
    public MaintenanceRetroactiveImpactDomain sourceDomain() {
        return MaintenanceRetroactiveImpactDomain.PAYMENT;
    }

    @Override
    protected ApiResponse<List<PaymentOrderResponse>> query(ImpactRequest request) {
        return client.getPaymentOrdersByBusinessId(request.policyId(), request.tenantId());
    }

    @Override
    protected List<MaintenanceRetroactiveImpactItem> toItems(ImpactRequest request, PaymentOrderResponse source) {
        requirePolicy(request, source.getPolicyId());
        String orderId = requireText("orderId", source.getOrderId());
        String status = requireText("status", source.getStatus());
        BigDecimal amount = source.getAmount();
        String currency = amount == null ? null : requireText("currency", source.getCurrency());
        LocalDateTime collectedAt = source.getPaymentDate() == null ? source.getCreatedAt() : source.getPaymentDate();
        List<MaintenanceRetroactiveImpactItem> items = new ArrayList<>();
        String collectionHash = MaintenanceRetroactiveImpactSourcePort.itemHash(
                orderId, source.getPolicyId(), value(amount), currency, status,
                source.getPaymentMethod(), source.getTransactionId(), value(collectedAt),
                value(source.getCreatedAt()), value(source.getUpdatedAt()));
        items.add(new MaintenanceRetroactiveImpactItem(
                "PAYMENT:COLLECTION:" + orderId, sourceDomain(), MaintenanceRetroactiveImpactType.COLLECTION,
                orderId, source.getTransactionId(), collectedAt, status, amount, currency,
                collectionSeverity(status), MaintenanceRetroactiveImpactItemStatus.PENDING,
                "追溯时点后存在收款订单", ITEM_EVIDENCE_VERSION, collectionHash));

        BigDecimal refundedAmount = source.getRefundedAmount();
        if (refundedAmount != null && refundedAmount.signum() > 0) {
            LocalDateTime refundedAt = source.getUpdatedAt() == null ? collectedAt : source.getUpdatedAt();
            String refundHash = MaintenanceRetroactiveImpactSourcePort.itemHash(
                    orderId, source.getPolicyId(), value(refundedAmount), currency, status,
                    source.getTransactionId(), value(refundedAt));
            items.add(new MaintenanceRetroactiveImpactItem(
                    "PAYMENT:REFUND:" + orderId, sourceDomain(), MaintenanceRetroactiveImpactType.REFUND,
                    orderId, source.getTransactionId(), refundedAt, status, refundedAmount, currency,
                    MaintenanceRetroactiveImpactSeverity.BLOCKING,
                    MaintenanceRetroactiveImpactItemStatus.PENDING,
                    "追溯时点后存在退款事实", ITEM_EVIDENCE_VERSION, refundHash));
        }
        return List.copyOf(items);
    }

    private MaintenanceRetroactiveImpactSeverity collectionSeverity(String status) {
        String normalized = status.toUpperCase(Locale.ROOT);
        return normalized.contains("SUCCESS") || normalized.contains("PAID") || normalized.contains("COMPLETED")
                ? MaintenanceRetroactiveImpactSeverity.BLOCKING
                : MaintenanceRetroactiveImpactSeverity.WARNING;
    }
}
