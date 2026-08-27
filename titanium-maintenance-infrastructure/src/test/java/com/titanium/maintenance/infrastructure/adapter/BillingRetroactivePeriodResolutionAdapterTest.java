package com.titanium.maintenance.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.billing.api.RetroactivePeriodAdjustmentApi;
import com.titanium.billing.api.response.RetroactivePeriodAdjustmentResolutionResponse;
import com.titanium.billing.api.response.RetroactivePeriodAdjustmentResolutionResponse.ResolutionLineResponse;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.port.BillingRetroactivePeriodResolutionPort.ResolutionRequest;
import com.titanium.metadata.response.ApiResponse;

class BillingRetroactivePeriodResolutionAdapterTest {

    @Test
    void shouldAcceptFullyReconciledResolution() {
        RetroactivePeriodAdjustmentApi api = mock(RetroactivePeriodAdjustmentApi.class);
        when(api.resolveClosedPeriods(eq("billing-batch-1"), any(), eq("tenant-1")))
                .thenReturn(ApiResponse.success(response("case-1")));

        var fact = new BillingRetroactivePeriodResolutionAdapter(api).resolve(request());

        assertEquals("billing-resolution-1", fact.billingResolutionId());
        assertEquals("posting-1", fact.lines().getFirst().postingReference());
    }

    @Test
    void shouldRejectResolutionWithDifferentCaseEcho() {
        RetroactivePeriodAdjustmentApi api = mock(RetroactivePeriodAdjustmentApi.class);
        when(api.resolveClosedPeriods(eq("billing-batch-1"), any(), eq("tenant-1")))
                .thenReturn(ApiResponse.success(response("case-other")));

        assertThrows(BusinessException.class,
                () -> new BillingRetroactivePeriodResolutionAdapter(api).resolve(request()));
    }

    private ResolutionRequest request() {
        return new ResolutionRequest(
                "tenant-1", "case-1", "policy-1", "billing-batch-1", hash('b'),
                "mrr-request-1", YearMonth.of(2026, 8), "结转至当前开放期间", "operator-1");
    }

    private RetroactivePeriodAdjustmentResolutionResponse response(String maintenanceId) {
        return new RetroactivePeriodAdjustmentResolutionResponse(
                "billing-resolution-1", "mrr-request-1", "billing-batch-1", "tenant-1",
                maintenanceId, "policy-1", hash('b'), "2026-08", "COMPLETED", 1,
                hash('q'), hash('r'), "结转至当前开放期间", "operator-1",
                LocalDateTime.of(2026, 8, 26, 14, 0), List.of(new ResolutionLineResponse(
                        "BILLING:bill-1", "2026-07", "2026-08", "DEBIT",
                        new BigDecimal("20.00"), "CNY", "posting-1", hash('z'), hash('l'))));
    }

    private String hash(char value) {
        return String.valueOf((char) ('a' + Math.floorMod(value, 6))).repeat(64);
    }
}
