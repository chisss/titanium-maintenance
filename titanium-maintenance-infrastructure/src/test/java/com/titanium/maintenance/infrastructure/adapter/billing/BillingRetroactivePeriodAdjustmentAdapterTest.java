package com.titanium.maintenance.infrastructure.adapter.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.billing.api.RetroactivePeriodAdjustmentApi;
import com.titanium.billing.api.response.retroactive.RetroactivePeriodAdjustmentResponse;
import com.titanium.billing.api.response.retroactive.RetroactivePeriodAdjustmentResponse.PeriodAdjustmentLineResponse;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodAdjustmentPort.AdjustmentRequest;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductPeriodDifference;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductRecalculationEvidence;
import com.titanium.metadata.response.ApiResponse;

class BillingRetroactivePeriodAdjustmentAdapterTest {

    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 7, 1, 0, 0);

    @Test
    void shouldAcceptFullyReconciledBillingFact() {
        RetroactivePeriodAdjustmentApi api = mock(RetroactivePeriodAdjustmentApi.class);
        when(api.post(any(), org.mockito.ArgumentMatchers.eq("tenant-1")))
                .thenReturn(ApiResponse.success(response("case-1")));

        var fact = new BillingRetroactivePeriodAdjustmentAdapter(api).adjust(request());

        assertEquals("billing-batch-1", fact.batchId());
        assertEquals("CLOSED_PERIOD_REVIEW", fact.periods().getFirst().status());
    }

    @Test
    void shouldRejectBillingFactWithDifferentCaseEcho() {
        RetroactivePeriodAdjustmentApi api = mock(RetroactivePeriodAdjustmentApi.class);
        when(api.post(any(), org.mockito.ArgumentMatchers.eq("tenant-1")))
                .thenReturn(ApiResponse.success(response("case-other")));

        assertThrows(BusinessException.class,
                () -> new BillingRetroactivePeriodAdjustmentAdapter(api).adjust(request()));
    }

    private AdjustmentRequest request() {
        return new AdjustmentRequest(
                "tenant-1", "case-1", "policy-1", "customer-1", "analysis-1", 1,
                hash('a'), "mbr-request-1", "operator-1", productEvidence());
    }

    private MaintenanceRetroactiveProductRecalculationEvidence productEvidence() {
        return new MaintenanceRetroactiveProductRecalculationEvidence(
                "product-recalculation-1", "PERIOD_V1", "calc-original", hash('b'),
                "calc-replacement", hash('c'), MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20.00"), "CNY", hash('d'), hash('e'), PERIOD_START.plusMinutes(1),
                List.of(new MaintenanceRetroactiveProductPeriodDifference(
                        "BILLING:bill-1", "bill-1", PERIOD_START, new BigDecimal("100.00"),
                        new BigDecimal("120.00"), MaintenanceBalanceDirection.DEBIT,
                        new BigDecimal("20.00"), "CNY", hash('f'), hash('a'))));
    }

    private RetroactivePeriodAdjustmentResponse response(String maintenanceId) {
        return new RetroactivePeriodAdjustmentResponse(
                "billing-batch-1", "mbr-request-1", "tenant-1", maintenanceId, "policy-1",
                "customer-1", "analysis-1", 1, hash('a'), "product-recalculation-1", "PERIOD_V1",
                hash('d'), hash('e'), "REVIEW_REQUIRED", 0, 1, hash('b'), hash('c'), "operator-1",
                PERIOD_START.plusMinutes(2), List.of(new PeriodAdjustmentLineResponse(
                        "BILLING:bill-1", "bill-1", "2026-07", PERIOD_START,
                        new BigDecimal("100.00"), new BigDecimal("120.00"), "DEBIT",
                        new BigDecimal("20.00"), "CNY", "CLOSED_PERIOD_REVIEW", hash('f'),
                        hash('a'), hash('b'))));
    }

    private String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
