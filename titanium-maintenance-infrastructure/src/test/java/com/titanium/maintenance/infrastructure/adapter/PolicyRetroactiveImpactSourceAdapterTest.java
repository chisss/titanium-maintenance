package com.titanium.maintenance.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.infrastructure.client.PolicyServiceClient;
import com.titanium.maintenance.port.MaintenanceRetroactiveImpactSourcePort.ImpactRequest;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.PolicyEndorsementResponse;

class PolicyRetroactiveImpactSourceAdapterTest {

    @Test
    void shouldReturnOnlySubsequentEndorsementsWithinFrozenScope() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        when(client.getEndorsements("policy-1", "tenant-1")).thenReturn(ApiResponse.success(List.of(
                endorsement("END-BEFORE", LocalDateTime.parse("2026-07-31T23:59:59"), "tenant-1", "policy-1"),
                endorsement("END-IN-SCOPE", LocalDateTime.parse("2026-08-20T10:00:00"), "tenant-1", "policy-1"),
                endorsement("END-AFTER", LocalDateTime.parse("2026-08-26T00:00:00"), "tenant-1", "policy-1"))));
        PolicyRetroactiveImpactSourceAdapter adapter = new PolicyRetroactiveImpactSourceAdapter(client);

        var evidence = adapter.collect(request());

        assertEquals(1, evidence.items().size());
        assertEquals("POLICY:END-IN-SCOPE", evidence.items().getFirst().itemId());
        assertEquals(MaintenanceRetroactiveImpactSeverity.BLOCKING,
                evidence.items().getFirst().severity());
        verify(client).getEndorsements("policy-1", "tenant-1");
    }

    @Test
    void shouldRejectAuthorityResponseWithDifferentTenant() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        when(client.getEndorsements("policy-1", "tenant-1")).thenReturn(ApiResponse.success(List.of(
                endorsement("END-001", LocalDateTime.parse("2026-08-20T10:00:00"),
                        "tenant-2", "policy-1"))));

        assertThrows(MaintenanceValidationException.class,
                () -> new PolicyRetroactiveImpactSourceAdapter(client).collect(request()));
    }

    private PolicyEndorsementResponse endorsement(
            String endorsementNo,
            LocalDateTime effectiveAt,
            String tenantId,
            String policyId) {
        return new PolicyEndorsementResponse(
                endorsementNo, policyId, "MAINTENANCE", "CONTACT", 8, effectiveAt,
                "联系方式变更", false, "case-source", "operator-1", effectiveAt, tenantId);
    }

    private ImpactRequest request() {
        return new ImpactRequest(
                "tenant-1", "case-1", "policy-1",
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-08-25T23:59:59"));
    }
}
