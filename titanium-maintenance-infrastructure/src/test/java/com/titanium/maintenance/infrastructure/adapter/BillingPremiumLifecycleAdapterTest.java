package com.titanium.maintenance.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.billing.api.response.PremiumLifecyclePostingResponse;
import com.titanium.billing.api.response.PremiumLifecycleReversalResponse;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.infrastructure.client.BillingPremiumLifecycleClient;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort;
import com.titanium.metadata.response.ApiResponse;

class BillingPremiumLifecycleAdapterTest {

    @Test
    void shouldMapFinancialSettlementFacts() {
        PremiumLifecyclePostingResponse response = new PremiumLifecyclePostingResponse(
                "posting-1", "adjustment-1", "hash-1", "ENDORSEMENT", "policy-1", "customer-1",
                "product-1", "calc-original", "calc-replacement", "CREDIT", new BigDecimal("53.00"),
                "CNY", "POSTED", "admin", LocalDateTime.of(2026, 8, 20, 12, 0), List.of(),
                "refund-instruction-1", "refund-order-1", "PROCESSING", 2, List.of());
        BillingPremiumLifecycleAdapter adapter = new BillingPremiumLifecycleAdapter(
                client(ApiResponse.success(response)));

        BillingPremiumLifecyclePort.PostingFact fact = adapter.post(request());

        assertEquals(MaintenanceBalanceDirection.CREDIT, fact.direction());
        assertEquals("refund-instruction-1", fact.refundInstructionId());
        assertEquals("refund-order-1", fact.refundOrderId());
        assertEquals("PROCESSING", fact.refundStatus());
        assertEquals(2, fact.commissionAdjustmentCount());
    }

    @Test
    void shouldRejectFailedOrEmptyBillingResponse() {
        BillingPremiumLifecycleAdapter failed = new BillingPremiumLifecycleAdapter(
                client(new ApiResponse<>("50000001", "failed", null)));
        BillingPremiumLifecycleAdapter empty = new BillingPremiumLifecycleAdapter(client(null));

        assertThrows(BusinessException.class, () -> failed.post(request()));
        assertThrows(BusinessException.class, () -> empty.post(request()));
    }

    @Test
    void shouldMapLifecycleReversalFact() {
        BillingPremiumLifecycleClient client = mock(BillingPremiumLifecycleClient.class);
        PremiumLifecycleReversalResponse response = new PremiumLifecycleReversalResponse(
                "reversal-1", "request-1", "a".repeat(64), "b".repeat(64),
                "posting-1", "c".repeat(64), "policy-1", "customer-1",
                "DEBIT", new BigDecimal("53.00"), "CNY", "POSTED",
                "客户取消项目", "operator-1", LocalDateTime.of(2026, 8, 26, 14, 0));
        when(client.reverse(anyString(), any(), anyString())).thenReturn(ApiResponse.success(response));
        BillingPremiumLifecycleAdapter adapter = new BillingPremiumLifecycleAdapter(client);

        BillingPremiumLifecyclePort.ReversalFact fact = adapter.reverse(
                new BillingPremiumLifecyclePort.ReversalRequest(
                        "1", "posting-1", "request-1", "客户取消项目", "operator-1"));

        assertEquals("reversal-1", fact.reversalId());
        assertEquals(MaintenanceBalanceDirection.DEBIT, fact.direction());
        assertEquals("REVERSED", fact.status());
    }

    @Test
    void shouldRejectUnexpectedLifecycleReversalStatus() {
        BillingPremiumLifecycleClient client = mock(BillingPremiumLifecycleClient.class);
        PremiumLifecycleReversalResponse response = new PremiumLifecycleReversalResponse(
                "reversal-1", "request-1", "a".repeat(64), "b".repeat(64),
                "posting-1", "c".repeat(64), "policy-1", "customer-1",
                "DEBIT", new BigDecimal("53.00"), "CNY", "PENDING",
                "客户取消项目", "operator-1", LocalDateTime.of(2026, 8, 26, 14, 0));
        when(client.reverse(anyString(), any(), anyString())).thenReturn(ApiResponse.success(response));
        BillingPremiumLifecycleAdapter adapter = new BillingPremiumLifecycleAdapter(client);

        assertThrows(BusinessException.class, () -> adapter.reverse(
                new BillingPremiumLifecyclePort.ReversalRequest(
                        "1", "posting-1", "request-1", "客户取消项目", "operator-1")));
    }

    private BillingPremiumLifecyclePort.PostingRequest request() {
        return new BillingPremiumLifecyclePort.PostingRequest(
                "1", "adjustment-1", "hash-1", "policy-1", "customer-1", "admin");
    }

    private BillingPremiumLifecycleClient client(ApiResponse<PremiumLifecyclePostingResponse> response) {
        BillingPremiumLifecycleClient client = mock(BillingPremiumLifecycleClient.class);
        when(client.post(any(), anyString())).thenReturn(response);
        return client;
    }
}
