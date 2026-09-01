package com.titanium.maintenance.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.port.product.ProductRetroactivePeriodRecalculationPort.AffectedPeriod;
import com.titanium.maintenance.port.product.ProductRetroactivePeriodRecalculationPort.RecalculationRequest;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.ProductPremiumCalculationApi;
import com.titanium.product.api.response.premium.RetroactivePremiumPeriodRecalculationResponse;
import com.titanium.product.api.response.premium.RetroactivePremiumPeriodRecalculationResponse.PeriodDifferenceResponse;

class ProductRetroactivePeriodRecalculationAdapterTest {

    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 7, 1, 0, 0);

    @Test
    void shouldAcceptFullyReconciledProductFact() {
        ProductPremiumCalculationApi api = mock(ProductPremiumCalculationApi.class);
        when(api.recalculateRetroactivePeriods(any(), org.mockito.ArgumentMatchers.eq("tenant-1")))
                .thenReturn(ApiResponse.success(response("policy-1")));

        var fact = new ProductRetroactivePeriodRecalculationAdapter(api).recalculate(request());

        assertEquals("product-recalculation-1", fact.recalculationId());
        assertEquals(MaintenanceBalanceDirection.DEBIT, fact.direction());
        assertEquals(new BigDecimal("120.00"), fact.periods().getFirst().recalculatedAmount());
    }

    @Test
    void shouldRejectProductFactWithDifferentPolicyEcho() {
        ProductPremiumCalculationApi api = mock(ProductPremiumCalculationApi.class);
        when(api.recalculateRetroactivePeriods(any(), org.mockito.ArgumentMatchers.eq("tenant-1")))
                .thenReturn(ApiResponse.success(response("policy-other")));

        assertThrows(BusinessException.class,
                () -> new ProductRetroactivePeriodRecalculationAdapter(api).recalculate(request()));
    }

    private RecalculationRequest request() {
        return new RecalculationRequest(
                "tenant-1", "case-1", "policy-1", "analysis-1", 1, hash('a'),
                "calc-original", "calc-replacement", LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 8, 26, 0, 0), List.of(new AffectedPeriod(
                        "BILLING:bill-1", "bill-1", PERIOD_START, new BigDecimal("100.00"),
                        "CNY", hash('b'))), "mpr-request-1");
    }

    private RetroactivePremiumPeriodRecalculationResponse response(String policyId) {
        return new RetroactivePremiumPeriodRecalculationResponse(
                "tenant-1", "product-recalculation-1", "PERIOD_V1", "mpr-request-1", "case-1",
                policyId, "analysis-1", 1, hash('a'), "product-1", "V1", "calc-original", hash('c'),
                "calc-replacement", hash('d'), LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 8, 26, 0, 0), "DEBIT", new BigDecimal("20.00"), "CNY",
                hash('e'), hash('f'), PERIOD_START.plusMinutes(1), List.of(new PeriodDifferenceResponse(
                        "BILLING:bill-1", "bill-1", PERIOD_START, new BigDecimal("100.00"),
                        new BigDecimal("120.00"), "DEBIT", new BigDecimal("20.00"), "CNY",
                        hash('b'), hash('a'))));
    }

    private String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
