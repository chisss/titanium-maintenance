package com.titanium.maintenance.infrastructure.adapter.retroactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.infrastructure.client.payment.PaymentRetroactiveImpactClient;
import com.titanium.maintenance.port.maintenance.MaintenanceRetroactiveImpactSourcePort.ImpactRequest;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.response.PaymentOrderResponse;

class PaymentRetroactiveImpactSourceAdapterTest {

    @Test
    void shouldExposeCompletedCollectionAndRefundAsBlockingItems() {
        PaymentRetroactiveImpactClient client = mock(PaymentRetroactiveImpactClient.class);
        when(client.getPaymentOrdersByBusinessId("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(List.of(order("payment-1", "policy-1"))));

        var evidence = new PaymentRetroactiveImpactSourceAdapter(client).collect(request());

        assertEquals(2, evidence.items().size());
        assertEquals(MaintenanceRetroactiveImpactType.COLLECTION,
                evidence.items().getFirst().impactType());
        assertEquals(MaintenanceRetroactiveImpactType.REFUND,
                evidence.items().get(1).impactType());
        evidence.items().forEach(item -> assertEquals(
                MaintenanceRetroactiveImpactSeverity.BLOCKING, item.severity()));
    }

    @Test
    void shouldRejectAuthorityResponseForDifferentPolicy() {
        PaymentRetroactiveImpactClient client = mock(PaymentRetroactiveImpactClient.class);
        when(client.getPaymentOrdersByBusinessId("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(List.of(order("payment-1", "policy-2"))));

        assertThrows(MaintenanceValidationException.class,
                () -> new PaymentRetroactiveImpactSourceAdapter(client).collect(request()));
    }

    private PaymentOrderResponse order(String id, String policyId) {
        PaymentOrderResponse response = new PaymentOrderResponse();
        response.setOrderId(id);
        response.setPolicyId(policyId);
        response.setAmount(new BigDecimal("300"));
        response.setRefundedAmount(new BigDecimal("50"));
        response.setCurrency("CNY");
        response.setPaymentMethod("BANK_CARD");
        response.setStatus("SUCCESS");
        response.setTransactionId("transaction-1");
        response.setPaymentDate(LocalDateTime.parse("2026-08-15T10:00:00"));
        response.setCreatedAt(LocalDateTime.parse("2026-08-15T09:00:00"));
        response.setUpdatedAt(LocalDateTime.parse("2026-08-16T10:00:00"));
        return response;
    }

    private ImpactRequest request() {
        return new ImpactRequest(
                "tenant-1", "case-1", "policy-1",
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-08-25T23:59:59"));
    }
}
