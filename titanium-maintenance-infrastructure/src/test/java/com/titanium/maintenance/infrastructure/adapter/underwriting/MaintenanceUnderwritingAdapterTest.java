package com.titanium.maintenance.infrastructure.adapter.underwriting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.port.maintenance.MaintenanceUnderwritingPort.AssessmentFact;
import com.titanium.maintenance.port.maintenance.MaintenanceUnderwritingPort.AssessmentRequest;
import com.titanium.maintenance.port.maintenance.MaintenanceUnderwritingPort.RiskFieldChange;
import com.titanium.underwriting.api.MaintenanceUnderwritingApi;
import com.titanium.underwriting.api.response.MaintenanceUnderwritingResponse;

class MaintenanceUnderwritingAdapterTest {

    private MaintenanceUnderwritingApi api;
    private MaintenanceUnderwritingAdapter adapter;

    @BeforeEach
    void setUp() {
        api = mock(MaintenanceUnderwritingApi.class);
        adapter = new MaintenanceUnderwritingAdapter(api);
    }

    @Test
    void shouldMapAndValidateAuthoritativeUnderwritingFact() {
        AssessmentRequest request = request();
        when(api.assess(any(), eq("tenant-1")))
                .thenReturn(ResponseEntity.ok(response(request, "CONDITIONAL_APPROVED")));

        AssessmentFact fact = adapter.assess(request);

        assertEquals(MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED, fact.conclusion());
        assertEquals(List.of("REVIEW_FIELD:insured.occupation"), fact.additionalConditions());
    }

    @Test
    void shouldRejectMismatchedEchoAndRemoteTimeout() {
        AssessmentRequest request = request();
        MaintenanceUnderwritingResponse mismatched = new MaintenanceUnderwritingResponse(
                "tenant-2", "case-1", "policy-1", 7L, "POLICY_INFO_CHANGE",
                "underwriting-1", request.idempotencyKey(), request.payloadHash(),
                "rule-v1", "model-v1", "APPROVED", List.of(), "通过",
                LocalDateTime.parse("2026-08-25T12:00:00"));
        when(api.assess(any(), eq("tenant-1"))).thenReturn(ResponseEntity.ok(mismatched));
        assertThrows(BusinessException.class, () -> adapter.assess(request));

        when(api.assess(any(), eq("tenant-1"))).thenThrow(new RuntimeException("timeout"));
        assertThrows(BusinessException.class, () -> adapter.assess(request));
    }

    private AssessmentRequest request() {
        return new AssessmentRequest(
                "tenant-1", "case-1", "policy-1", 7L, "product-1", "product-v3", "plan-v2",
                "POLICY_INFO_CHANGE", "config-v4", "a".repeat(64), true,
                List.of(new RiskFieldChange(
                        "insured-1", "insured.occupation", "TEXT", "1", "4",
                        "UW_CONDITIONAL_OCCUPATION")),
                "case-1:task-1", "maintenance-service");
    }

    private MaintenanceUnderwritingResponse response(AssessmentRequest request, String conclusion) {
        return new MaintenanceUnderwritingResponse(
                request.tenantId(), request.maintenanceId(), request.policyId(),
                request.policyBaselineVersion(), request.itemCode(), "underwriting-1",
                request.idempotencyKey(), request.payloadHash(), "rule-v1", "model-v1", conclusion,
                List.of("REVIEW_FIELD:insured.occupation"), "附加条件通过",
                LocalDateTime.parse("2026-08-25T12:00:00"));
    }
}
