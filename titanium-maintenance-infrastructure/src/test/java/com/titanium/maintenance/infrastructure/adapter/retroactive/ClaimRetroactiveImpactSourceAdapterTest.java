package com.titanium.maintenance.infrastructure.adapter.retroactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.claim.api.response.ClaimResponse;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.infrastructure.client.claim.ClaimRetroactiveImpactClient;
import com.titanium.maintenance.port.maintenance.MaintenanceRetroactiveImpactSourcePort.ImpactRequest;
import com.titanium.metadata.response.ApiResponse;

class ClaimRetroactiveImpactSourceAdapterTest {

    @Test
    void shouldClassifyClaimsAndBenefitsWithoutInventingCurrency() {
        ClaimRetroactiveImpactClient client = mock(ClaimRetroactiveImpactClient.class);
        when(client.getClaimsByPolicyId("policy-1", "tenant-1")).thenReturn(ApiResponse.success(List.of(
                claim("claim-1", "MEDICAL", LocalDateTime.parse("2026-08-10T10:00:00"), "policy-1"),
                claim("claim-2", "DEATH_BENEFIT", LocalDateTime.parse("2026-08-20T10:00:00"), "policy-1"),
                claim("claim-before", "MEDICAL", LocalDateTime.parse("2026-07-31T10:00:00"), "policy-1"))));

        var evidence = new ClaimRetroactiveImpactSourceAdapter(client).collect(request());

        assertEquals(2, evidence.items().size());
        assertEquals(MaintenanceRetroactiveImpactType.CLAIM,
                evidence.items().getFirst().impactType());
        assertEquals(MaintenanceRetroactiveImpactType.BENEFIT,
                evidence.items().get(1).impactType());
        evidence.items().forEach(item -> {
            assertNull(item.amount());
            assertNull(item.currency());
        });
    }

    @Test
    void shouldRejectAuthorityResponseForDifferentPolicy() {
        ClaimRetroactiveImpactClient client = mock(ClaimRetroactiveImpactClient.class);
        when(client.getClaimsByPolicyId("policy-1", "tenant-1")).thenReturn(ApiResponse.success(List.of(
                claim("claim-1", "MEDICAL", LocalDateTime.parse("2026-08-10T10:00:00"), "policy-2"))));

        assertThrows(MaintenanceValidationException.class,
                () -> new ClaimRetroactiveImpactSourceAdapter(client).collect(request()));
    }

    private ClaimResponse claim(String id, String type, LocalDateTime occurredAt, String policyId) {
        ClaimResponse response = new ClaimResponse();
        response.setClaimId(id);
        response.setPolicyId(policyId);
        response.setClaimNumber("NO-" + id);
        response.setClaimType(type);
        response.setIncidentDate(occurredAt);
        response.setClaimAmount(new BigDecimal("1000"));
        response.setStatus("OPEN");
        response.setCreatedAt(occurredAt);
        return response;
    }

    private ImpactRequest request() {
        return new ImpactRequest(
                "tenant-1", "case-1", "policy-1",
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-08-25T23:59:59"));
    }
}
