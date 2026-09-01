package com.titanium.maintenance.infrastructure.adapter.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.infrastructure.client.payment.PaymentPremiumCollectionClient;
import com.titanium.maintenance.port.payment.PaymentPremiumCollectionPort.CollectionRequest;
import com.titanium.metadata.errorcode.PaymentErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.response.PaymentOrderResponse;

class PaymentPremiumCollectionAdapterTest {

    @Test
    void shouldReuseExistingDeterministicPaymentOrder() {
        PaymentPremiumCollectionClient client = mock(PaymentPremiumCollectionClient.class);
        when(client.get("payment-1", "tenant-1")).thenReturn(ApiResponse.success(response("payment-1")));
        PaymentPremiumCollectionAdapter adapter = new PaymentPremiumCollectionAdapter(client);

        var fact = adapter.create(request());

        assertEquals("payment-1", fact.paymentOrderId());
        assertEquals("PENDING", fact.status());
        verify(client, never()).create(any(), any());
    }

    @Test
    void shouldCreateWhenDeterministicPaymentOrderDoesNotExist() {
        PaymentPremiumCollectionClient client = mock(PaymentPremiumCollectionClient.class);
        when(client.get("payment-1", "tenant-1"))
                .thenReturn(ApiResponse.error(
                        PaymentErrorCode.PAYMENT_ORDER_NOT_EXIST, "支付订单不存在: payment-1"));
        when(client.create(any(), any())).thenReturn(ApiResponse.success(response("payment-1")));
        PaymentPremiumCollectionAdapter adapter = new PaymentPremiumCollectionAdapter(client);

        var fact = adapter.create(request());

        assertEquals(new BigDecimal("20"), fact.amount());
        verify(client).create(any(), any());
    }

    @Test
    void shouldNotCreateWhenPaymentLookupFailsForAnotherReason() {
        PaymentPremiumCollectionClient client = mock(PaymentPremiumCollectionClient.class);
        when(client.get("payment-1", "tenant-1"))
                .thenReturn(new ApiResponse<>("PAYMENT_REMOTE_ERROR", "payment unavailable", null));
        PaymentPremiumCollectionAdapter adapter = new PaymentPremiumCollectionAdapter(client);

        assertThrows(BusinessException.class, () -> adapter.create(request()));

        verify(client, never()).create(any(), any());
    }

    @Test
    void shouldRejectMismatchedPaymentOrderIdentity() {
        PaymentPremiumCollectionClient client = mock(PaymentPremiumCollectionClient.class);
        when(client.get("payment-1", "tenant-1"))
                .thenReturn(ApiResponse.success(response("another-payment")));
        PaymentPremiumCollectionAdapter adapter = new PaymentPremiumCollectionAdapter(client);

        assertThrows(BusinessException.class, () -> adapter.get("tenant-1", "payment-1"));
    }

    private CollectionRequest request() {
        return new CollectionRequest(
                "tenant-1", "payment-1", "policy-1", "customer-1",
                new BigDecimal("20"), "CNY", "BANK", "保全追加保费");
    }

    private PaymentOrderResponse response(String paymentOrderId) {
        PaymentOrderResponse response = new PaymentOrderResponse();
        response.setOrderId(paymentOrderId);
        response.setPolicyId("policy-1");
        response.setCustomerId("customer-1");
        response.setAmount(new BigDecimal("20"));
        response.setCurrency("CNY");
        response.setPaymentMethod("BANK");
        response.setStatus("PENDING");
        return response;
    }
}
