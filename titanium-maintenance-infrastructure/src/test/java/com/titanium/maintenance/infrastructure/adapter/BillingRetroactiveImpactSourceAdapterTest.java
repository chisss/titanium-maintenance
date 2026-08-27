package com.titanium.maintenance.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.billing.api.response.BillResponse;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.infrastructure.client.BillingRetroactiveImpactClient;
import com.titanium.maintenance.port.MaintenanceRetroactiveImpactSourcePort.ImpactRequest;
import com.titanium.metadata.response.ApiResponse;

class BillingRetroactiveImpactSourceAdapterTest {

    @Test
    void shouldClassifyPaidAndRenewalBillsAndFilterScope() {
        BillingRetroactiveImpactClient client = mock(BillingRetroactiveImpactClient.class);
        when(client.getBillsByPolicyId("policy-1", "tenant-1")).thenReturn(ApiResponse.success(List.of(
                bill("bill-paid", "PREMIUM", LocalDate.of(2026, 8, 10), new BigDecimal("100")),
                bill("bill-renewal", "RENEWAL", LocalDate.of(2026, 8, 20), BigDecimal.ZERO),
                bill("bill-before", "PREMIUM", LocalDate.of(2026, 7, 31), BigDecimal.ZERO))));

        var evidence = new BillingRetroactiveImpactSourceAdapter(client).collect(request());

        assertEquals(2, evidence.items().size());
        assertEquals(MaintenanceRetroactiveImpactSeverity.BLOCKING,
                evidence.items().getFirst().severity());
        assertEquals(MaintenanceRetroactiveImpactType.RENEWAL,
                evidence.items().get(1).impactType());
        assertEquals(MaintenanceRetroactiveImpactSeverity.WARNING,
                evidence.items().get(1).severity());
    }

    @Test
    void shouldRejectAuthorityResponseForDifferentPolicy() {
        BillingRetroactiveImpactClient client = mock(BillingRetroactiveImpactClient.class);
        BillResponse bill = bill("bill-1", "PREMIUM", LocalDate.of(2026, 8, 10), BigDecimal.ZERO);
        bill.setPolicyId("policy-2");
        when(client.getBillsByPolicyId("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(List.of(bill)));

        assertThrows(MaintenanceValidationException.class,
                () -> new BillingRetroactiveImpactSourceAdapter(client).collect(request()));
    }

    private BillResponse bill(String id, String type, LocalDate issueDate, BigDecimal paidAmount) {
        BillResponse response = new BillResponse();
        response.setBillId(id);
        response.setPolicyId("policy-1");
        response.setBillingType(type);
        response.setAmount(new BigDecimal("200"));
        response.setCurrency("CNY");
        response.setPaidAmount(paidAmount);
        response.setUnpaidAmount(new BigDecimal("200").subtract(paidAmount));
        response.setIssueDate(issueDate);
        response.setStatus(paidAmount.signum() > 0 ? "PAID" : "PENDING");
        response.setCreatedAt(issueDate.atStartOfDay());
        return response;
    }

    private ImpactRequest request() {
        return new ImpactRequest(
                "tenant-1", "case-1", "policy-1",
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-08-25T23:59:59"));
    }
}
