package com.titanium.maintenance.infrastructure.adapter.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.infrastructure.client.policy.PolicyServiceClient;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.ApplicationRequest;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.FieldChange;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.RetroactiveEvidence;
import com.titanium.maintenance.valueobject.effect.MaintenanceAmountEffect;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.pricing.ChargeDirection;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.maintenance.ApplyPolicyMaintenanceRequest;
import com.titanium.policy.api.request.maintenance.PolicyMaintenanceAmountRequest;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceApplicationResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceAppliedFieldResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceAppliedSnapshotResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceRetroactiveEvidenceResponse;

class PolicyMaintenanceApplicationAdapterTest {

    @Test
    void shouldTranslateAndValidatePolicyReceipt() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        ApplicationRequest request = request();
        when(client.applyMaintenance(
                eq("policy-1"), any()))
                .thenReturn(ApiResponse.success(response(request, 8)));

        var fact = new PolicyMaintenanceApplicationAdapter(client).apply(request);

        assertEquals("END-001", fact.endorsementNo());
        assertEquals(8, fact.appliedSnapshot().policyVersion());
        assertEquals("13900000000", fact.appliedFields().getFirst().canonicalValue());
    }

    /**
     * 金额型生效载荷必须原样翻译成 Policy 契约的金额事实（收付方向按 metadata 共享枚举 code 跨域传码）。
     * 缺此映射时保单域收到的是「无字段、无状态动作、无金额」的空交易，会被直接拒绝。
     */
    @Test
    void shouldTranslateRecordedDifferenceIntoPolicyAmountRequest() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        ApplicationRequest request = amountRequest();
        ArgumentCaptor<ApplyPolicyMaintenanceRequest> captured =
                ArgumentCaptor.forClass(ApplyPolicyMaintenanceRequest.class);
        when(client.applyMaintenance(eq("policy-1"), captured.capture()))
                .thenReturn(ApiResponse.success(amountResponse(request)));

        new PolicyMaintenanceApplicationAdapter(client).apply(request);

        PolicyMaintenanceAmountRequest amount = captured.getValue().amountFact();
        assertNotNull(amount, "金额型案件不得以空载荷外发");
        assertEquals("ADDITIONAL_PAYMENT", amount.itemCode());
        assertEquals(0, amount.amount().compareTo(new BigDecimal("1000.00")));
        assertEquals(ChargeDirection.DEBIT, amount.direction());
        assertEquals("CNY", amount.currency());
        assertEquals("POSTING-1", amount.sourceRef());
    }

    @Test
    void shouldRejectReceiptWithMismatchedSnapshotVersion() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        ApplicationRequest request = request();
        when(client.applyMaintenance(any(), any()))
                .thenReturn(ApiResponse.success(response(request, 9)));

        assertThrows(MaintenanceValidationException.class,
                () -> new PolicyMaintenanceApplicationAdapter(client).apply(request));
    }

    @Test
    void shouldRejectReceiptWithMismatchedAppliedFieldContract() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        ApplicationRequest request = request();
        PolicyMaintenanceApplicationResponse valid = response(request, 8);
        PolicyMaintenanceApplicationResponse invalid = new PolicyMaintenanceApplicationResponse(
                valid.requestId(), valid.endorsementNo(), valid.expectedPolicyVersion(),
                valid.actualPolicyVersion(), valid.applicationHash(), valid.appliedSnapshot(),
                List.of(new PolicyMaintenanceAppliedFieldResponse(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        "ENUM", "13900000000")),
                valid.appliedAt());
        when(client.applyMaintenance(any(), any()))
                .thenReturn(ApiResponse.success(invalid));

        assertThrows(MaintenanceValidationException.class,
                () -> new PolicyMaintenanceApplicationAdapter(client).apply(request));
    }

    @Test
    void shouldRoundTripRetroactiveEvidence() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        ApplicationRequest request = retroactiveRequest();
        when(client.applyMaintenance(any(), any()))
                .thenReturn(ApiResponse.success(retroactiveResponse(request, "e".repeat(64))));

        var fact = new PolicyMaintenanceApplicationAdapter(client).apply(request);

        assertEquals("billing-resolution-1", fact.retroactiveEvidence().billingResolutionId());
    }

    @Test
    void shouldRejectMismatchedRetroactiveEvidenceInReceipt() {
        PolicyServiceClient client = mock(PolicyServiceClient.class);
        ApplicationRequest request = retroactiveRequest();
        when(client.applyMaintenance(any(), any()))
                .thenReturn(ApiResponse.success(retroactiveResponse(request, "f".repeat(64))));

        assertThrows(MaintenanceValidationException.class,
                () -> new PolicyMaintenanceApplicationAdapter(client).apply(request));
    }

    /** 金额型案件请求：无字段变更、无状态动作，生效载荷就是这笔已登记的收付差额。 */
    private ApplicationRequest amountRequest() {
        MaintenanceAmountEffect amountEffect = new MaintenanceAmountEffect(
                "ADDITIONAL_PAYMENT", new BigDecimal("1000.00"), MaintenanceBalanceDirection.DEBIT,
                "CNY", "POSTING-1");
        return new ApplicationRequest(
                "tenant-1", "policy-1", "effect-request-amount", "maintenance-1", 7,
                null, "a".repeat(64), "IMMEDIATE",
                LocalDateTime.parse("2026-09-28T10:00:00"),
                "maintenance=maintenance-1;items=ADDITIONAL_PAYMENT;fields=;action=NONE;amount=DEBIT:1000.00:CNY",
                List.of(),
                amountEffect,
                null,
                PolicyMaintenanceAction.NONE,
                null, null, null,
                "operator-1");
    }

    private PolicyMaintenanceApplicationResponse amountResponse(ApplicationRequest request) {
        return new PolicyMaintenanceApplicationResponse(
                request.requestId(), "END-AMOUNT-001", 7, 8, "b".repeat(64),
                new PolicyMaintenanceAppliedSnapshotResponse(
                        "axon-event://policy/tenant-1/policy-1?version=8", "c".repeat(64),
                        8, OffsetDateTime.parse("2026-09-28T10:00:00+08:00")),
                List.of(),
                LocalDateTime.parse("2026-09-28T10:00:00"));
    }

    private ApplicationRequest request() {
        return new ApplicationRequest(
                "tenant-1", "policy-1", "effect-request-1", "maintenance-1", 7,
                null, "a".repeat(64), "IMMEDIATE",
                LocalDateTime.parse("2026-08-25T10:00:00"),
                "maintenance=maintenance-1;fields=policy.holder.mobile",
                List.of(new FieldChange(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        "TEXT", "13900000000")),
                "operator-1");
    }

    private ApplicationRequest retroactiveRequest() {
        RetroactiveEvidence evidence = new RetroactiveEvidence(
                "analysis-1", 1, "a".repeat(64), "period-recalculation-1", 1,
                "product-recalculation-1", "PERIOD_V1", "b".repeat(64), "c".repeat(64),
                "billing-batch-1", "d".repeat(64), "REVIEW_REQUIRED", "billing-resolution-1",
                "e".repeat(64), "2026-08", 1);
        return new ApplicationRequest(
                "tenant-1", "policy-1", "effect-request-retroactive", "maintenance-1", 7,
                null, "a".repeat(64), "RETROACTIVE",
                LocalDateTime.parse("2026-07-01T00:00:00"),
                "maintenance=maintenance-1;fields=policy.holder.mobile",
                List.of(new FieldChange(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        "TEXT", "13900000000")),
                null,
                null,
                com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction.NONE,
                null, null, evidence, "operator-1");
    }

    private PolicyMaintenanceApplicationResponse response(ApplicationRequest request, long snapshotVersion) {
        return new PolicyMaintenanceApplicationResponse(
                request.requestId(), "END-001", 7, 8, "b".repeat(64),
                new PolicyMaintenanceAppliedSnapshotResponse(
                        "axon-event://policy/tenant-1/policy-1?version=8", "c".repeat(64),
                        snapshotVersion, OffsetDateTime.parse("2026-08-25T10:00:00+08:00")),
                List.of(new PolicyMaintenanceAppliedFieldResponse(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        "TEXT", "13900000000")),
                LocalDateTime.parse("2026-08-25T10:00:00"));
    }

    private PolicyMaintenanceApplicationResponse retroactiveResponse(
            ApplicationRequest request,
            String resolutionResultHash) {
        RetroactiveEvidence evidence = request.retroactiveEvidence();
        return new PolicyMaintenanceApplicationResponse(
                request.requestId(), "END-RETROACTIVE", 7, 8, "b".repeat(64),
                new PolicyMaintenanceAppliedSnapshotResponse(
                        "axon-event://policy/tenant-1/policy-1?version=8", "c".repeat(64),
                        8, OffsetDateTime.parse("2026-08-25T10:00:00+08:00")),
                List.of(new PolicyMaintenanceAppliedFieldResponse(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        "TEXT", "13900000000")),
                LocalDateTime.parse("2026-08-25T10:00:00"),
                com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction.NONE, null, null,
                new PolicyMaintenanceRetroactiveEvidenceResponse(
                        evidence.analysisId(), evidence.analysisVersion(), evidence.analysisResultHash(),
                        evidence.periodRecalculationId(), evidence.periodRecalculationVersion(),
                        evidence.productRecalculationId(), evidence.productRecalculationVersion(),
                        evidence.productInputHash(), evidence.productResultHash(), evidence.billingBatchId(),
                        evidence.billingBatchResultHash(), evidence.billingStatus(),
                        evidence.billingResolutionId(), resolutionResultHash,
                        evidence.targetAccountingPeriod(), evidence.resolvedLineCount()));
    }
}
