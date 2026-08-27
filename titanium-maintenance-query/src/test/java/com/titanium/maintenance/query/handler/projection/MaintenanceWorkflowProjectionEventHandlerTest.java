package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewGate;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.event.MaintenanceEffectStatusChangedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowInitializedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowTaskTransitionedEvent;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.workflow.MaintenanceAppliedFieldEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceBillingPostingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceFundSettlementEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyApplicationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePremiumQuoteEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceReviewGateEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceUnderwritingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowOperation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowReviewEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;

class MaintenanceWorkflowProjectionEventHandlerTest {

    @Test
    void shouldCompleteTerminalTasksAfterPolicyApplication() {
        MaintenanceWorkflowTaskViewRepository repository = mock(MaintenanceWorkflowTaskViewRepository.class);
        MaintenanceWorkflowProjectionEventHandler handler =
                new MaintenanceWorkflowProjectionEventHandler(repository);
        MaintenanceWorkflowTaskView terminal = view("task-complete");
        terminal.setStepType(MaintenanceStepType.COMPLETE);
        terminal.setStatus(MaintenanceWorkflowTaskStatus.PENDING);
        MaintenanceWorkflowTaskView skipped = view("task-skipped-complete");
        skipped.setStepType(MaintenanceStepType.COMPLETE);
        skipped.setStatus(MaintenanceWorkflowTaskStatus.SKIPPED);
        when(repository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "case-1"))
                .thenReturn(List.of(terminal, skipped));

        handler.on(new MaintenanceEffectStatusChangedEvent(
                MaintenanceId.of("case-1"), "task-effect", MaintenanceEffectStatus.EFFECTING,
                MaintenanceEffectStatus.APPLIED, "Policy 权威回执已记录",
                LocalDateTime.parse("2026-08-25T15:00:00"), "operator-1", "tenant-1"));

        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, terminal.getStatus());
        assertEquals(MaintenanceWorkflowTaskStatus.SKIPPED, skipped.getStatus());
        verify(repository).saveAll(List.of(terminal));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldProjectTenantScopedWorkflowTasks() {
        MaintenanceWorkflowTaskViewRepository repository = mock(MaintenanceWorkflowTaskViewRepository.class);
        MaintenanceWorkflowProjectionEventHandler handler =
                new MaintenanceWorkflowProjectionEventHandler(repository);
        LocalDateTime initializedAt = LocalDateTime.parse("2026-08-25T10:00:00");
        MaintenanceWorkflowTask task = new MaintenanceWorkflowTask(
                "case-1:POLICY_INFO_CHANGE:DATA_ENTRY", "POLICY_INFO_CHANGE", 0, 2,
                MaintenanceStepType.DATA_ENTRY, MaintenanceStepMode.REQUIRED, null,
                MaintenanceWorkflowTaskStatus.READY);

        handler.on(new MaintenanceWorkflowInitializedEvent(
                MaintenanceId.of("case-1"), List.of(task), initializedAt, "operator-1", "tenant-1"));

        ArgumentCaptor<List<MaintenanceWorkflowTaskView>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        MaintenanceWorkflowTaskView view = captor.getValue().getFirst();
        assertEquals("tenant-1", view.getTenantId());
        assertEquals("case-1", view.getMaintenanceId());
        assertEquals(MaintenanceWorkflowTaskStatus.READY, view.getStatus());
        assertEquals(initializedAt, view.getCreateTime());
    }

    @Test
    void shouldProjectTransitionAndActivatedTaskEvidence() {
        MaintenanceWorkflowTaskViewRepository repository = mock(MaintenanceWorkflowTaskViewRepository.class);
        MaintenanceWorkflowProjectionEventHandler handler =
                new MaintenanceWorkflowProjectionEventHandler(repository);
        MaintenanceWorkflowTaskView currentView = view("task-current");
        MaintenanceWorkflowTaskView nextView = view("task-next");
        when(repository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "case-1", "task-current"))
                .thenReturn(Optional.of(currentView));
        when(repository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "case-1", "task-next"))
                .thenReturn(Optional.of(nextView));
        MaintenanceWorkflowTask before = task(
                "task-current", 1, MaintenanceWorkflowTaskStatus.IN_PROGRESS);
        MaintenanceWorkflowOperation operation = MaintenanceWorkflowOperation.create(
                "operation-1", MaintenanceWorkflowAction.COMPLETE, "task-current",
                null, null, "DATA_RECORDED", "录入完成",
                LocalDateTime.parse("2026-08-25T11:30:00"), "operator-1");
        MaintenanceWorkflowTask after = before.complete(operation);
        MaintenanceWorkflowTask nextBefore = task(
                "task-next", 2, MaintenanceWorkflowTaskStatus.PENDING);
        MaintenanceWorkflowTask nextAfter = nextBefore.activate(operation);

        handler.on(new MaintenanceWorkflowTaskTransitionedEvent(
                MaintenanceId.of("case-1"), before, after, nextBefore, nextAfter,
                operation.operationId(), operation.payloadHash(), operation.operatedAt(),
                operation.operatedBy(), "tenant-1"));

        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, currentView.getStatus());
        assertEquals("operation-1", currentView.getLastOperationId());
        assertEquals("DATA_RECORDED", currentView.getLastResultCode());
        assertEquals(MaintenanceWorkflowTaskStatus.READY, nextView.getStatus());
        verify(repository).save(currentView);
        verify(repository).save(nextView);
    }

    @Test
    void shouldProjectStructuredAutomaticReviewEvidence() {
        MaintenanceWorkflowTaskViewRepository repository = mock(MaintenanceWorkflowTaskViewRepository.class);
        MaintenanceWorkflowProjectionEventHandler handler =
                new MaintenanceWorkflowProjectionEventHandler(repository);
        MaintenanceWorkflowTaskView currentView = view("task-review");
        when(repository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "case-1", "task-review"))
                .thenReturn(Optional.of(currentView));
        MaintenanceWorkflowTask before = new MaintenanceWorkflowTask(
                "task-review", "POLICY_INFO_CHANGE", 0, 2,
                MaintenanceStepType.REVIEW, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.READY);
        MaintenanceWorkflowReviewEvidence evidence = new MaintenanceWorkflowReviewEvidence(
                MaintenanceReviewMode.AUTOMATIC, MaintenanceReviewDecision.APPROVE,
                "APPROVAL_STANDARD", "policy-v1",
                Arrays.stream(MaintenanceReviewGate.values())
                        .map(gate -> new MaintenanceReviewGateEvidence(
                                gate, true, "a".repeat(64), gate.getCode() + "_PASSED"))
                        .toList(),
                "七类自动审核门禁全部通过",
                LocalDateTime.parse("2026-08-25T11:30:00"), "review-engine");
        MaintenanceWorkflowOperation operation = MaintenanceWorkflowOperation.create(
                "operation-review", MaintenanceWorkflowAction.DECIDE_REVIEW, "task-review",
                evidence.policyVersion(), evidence.contentHash(), evidence.decision().getCode(),
                evidence.comment(), evidence.decidedAt(), evidence.decidedBy());
        MaintenanceWorkflowTask after = before.decideReview(evidence, operation);

        handler.on(new MaintenanceWorkflowTaskTransitionedEvent(
                MaintenanceId.of("case-1"), before, after, null, null,
                operation.operationId(), operation.payloadHash(), operation.operatedAt(),
                operation.operatedBy(), "tenant-1"));

        assertEquals(MaintenanceReviewMode.AUTOMATIC, currentView.getReviewMode());
        assertEquals(MaintenanceReviewDecision.APPROVE, currentView.getReviewDecision());
        assertEquals(evidence.contentHash(), currentView.getReviewContextHash());
        assertTrue(currentView.getReviewGateEvidenceJson().contains("CHANNEL"));
        verify(repository).save(currentView);
    }

    @Test
    void shouldProjectStructuredUnderwritingEvidence() {
        MaintenanceWorkflowTaskViewRepository repository = mock(MaintenanceWorkflowTaskViewRepository.class);
        MaintenanceWorkflowProjectionEventHandler handler =
                new MaintenanceWorkflowProjectionEventHandler(repository);
        MaintenanceWorkflowTaskView currentView = view("task-underwriting");
        when(repository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "case-1", "task-underwriting"))
                .thenReturn(Optional.of(currentView));
        MaintenanceWorkflowTask before = new MaintenanceWorkflowTask(
                "task-underwriting", "POLICY_INFO_CHANGE", 0, 2,
                MaintenanceStepType.UNDERWRITING, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.READY);
        MaintenanceUnderwritingEvidence evidence = new MaintenanceUnderwritingEvidence(
                "underwriting-1", "b".repeat(64), "rule-v1", "model-v1",
                MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED,
                List.of("REVIEW_FIELD:insured.occupation"), "附加条件通过",
                LocalDateTime.parse("2026-08-25T12:00:00"));
        MaintenanceWorkflowOperation operation = MaintenanceWorkflowOperation.create(
                "operation-underwriting", MaintenanceWorkflowAction.DECIDE_UNDERWRITING,
                "task-underwriting", evidence.ruleVersion(), evidence.contentHash(),
                evidence.conclusion().getCode(), evidence.summary(),
                LocalDateTime.parse("2026-08-25T12:00:00"), "underwriting-service");
        MaintenanceWorkflowTask after = before.decideUnderwriting(evidence, operation);

        handler.on(new MaintenanceWorkflowTaskTransitionedEvent(
                MaintenanceId.of("case-1"), before, after, null, null,
                operation.operationId(), operation.payloadHash(), operation.operatedAt(),
                operation.operatedBy(), "tenant-1"));

        assertEquals("underwriting-1", currentView.getUnderwritingCaseId());
        assertEquals(MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED,
                currentView.getUnderwritingConclusion());
        assertTrue(currentView.getUnderwritingConditionsJson().contains("insured.occupation"));
        verify(repository).save(currentView);
    }

    @Test
    void shouldProjectCurrentPremiumQuoteCheckpoint() {
        MaintenanceWorkflowTaskViewRepository repository = mock(MaintenanceWorkflowTaskViewRepository.class);
        MaintenanceWorkflowProjectionEventHandler handler =
                new MaintenanceWorkflowProjectionEventHandler(repository);
        MaintenanceWorkflowTaskView currentView = view("task-fee");
        when(repository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "case-1", "task-fee"))
                .thenReturn(Optional.of(currentView));
        MaintenanceWorkflowTask before = new MaintenanceWorkflowTask(
                "task-fee", "POLICY_INFO_CHANGE", 0, 2,
                MaintenanceStepType.FEE_SETTLEMENT, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.READY);
        LocalDateTime quotedAt = LocalDateTime.parse("2026-08-25T12:00:00");
        MaintenancePremiumQuoteEvidence evidence = new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.QUOTED, "quote-1", "a".repeat(64), "b".repeat(64),
                "original", "c".repeat(64), "replacement", "d".repeat(64),
                "plan-v2", "e".repeat(64), "f".repeat(64), "DEBIT 20 CNY; lines=1",
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "CNY",
                quotedAt, quotedAt.plusHours(24));
        MaintenanceWorkflowOperation operation = MaintenanceWorkflowOperation.create(
                "operation-quote", MaintenanceWorkflowAction.RECORD_PREMIUM_QUOTE,
                "task-fee", evidence.evidenceVersion(), evidence.contentHash(),
                evidence.status().getCode(), evidence.detailSummary(), quotedAt, "pricing-service");
        MaintenanceWorkflowTask after = before.recordPremiumQuote(evidence, operation);

        handler.on(new MaintenanceWorkflowTaskTransitionedEvent(
                MaintenanceId.of("case-1"), before, after, null, null,
                operation.operationId(), operation.payloadHash(), operation.operatedAt(),
                operation.operatedBy(), "tenant-1"));

        assertEquals(MaintenancePremiumQuoteStatus.QUOTED, currentView.getPremiumQuoteStatus());
        assertEquals("quote-1", currentView.getPremiumQuoteId());
        assertEquals(new BigDecimal("20"), currentView.getPremiumQuoteAmount());
        assertEquals(quotedAt.plusHours(24), currentView.getPremiumQuoteValidUntil());
        verify(repository).save(currentView);
    }

    @Test
    void shouldProjectBillingPostingAndFundSettlementEvidenceSeparately() {
        MaintenanceWorkflowTaskViewRepository repository = mock(MaintenanceWorkflowTaskViewRepository.class);
        MaintenanceWorkflowProjectionEventHandler handler =
                new MaintenanceWorkflowProjectionEventHandler(repository);
        MaintenanceWorkflowTaskView currentView = view("task-fee");
        when(repository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "case-1", "task-fee"))
                .thenReturn(Optional.of(currentView));
        LocalDateTime quotedAt = LocalDateTime.parse("2026-08-25T12:00:00");
        MaintenanceWorkflowTask ready = new MaintenanceWorkflowTask(
                "task-fee", "POLICY_INFO_CHANGE", 0, 2,
                MaintenanceStepType.FEE_SETTLEMENT, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.READY);
        MaintenancePremiumQuoteEvidence quote = new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.QUOTED, "quote-1", "a".repeat(64), "b".repeat(64),
                "original", "c".repeat(64), "replacement", "d".repeat(64),
                "plan-v2", "e".repeat(64), "f".repeat(64), "DEBIT 20 CNY; lines=1",
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "CNY",
                quotedAt, quotedAt.plusHours(24));
        MaintenanceWorkflowOperation quoteOperation = MaintenanceWorkflowOperation.create(
                "operation-quote", MaintenanceWorkflowAction.RECORD_PREMIUM_QUOTE,
                "task-fee", quote.evidenceVersion(), quote.contentHash(), quote.status().getCode(),
                quote.detailSummary(), quotedAt, "pricing-service");
        MaintenanceWorkflowTask quoted = ready.recordPremiumQuote(quote, quoteOperation);
        MaintenanceBillingPostingEvidence posting = new MaintenanceBillingPostingEvidence(
                "posting-1", "quote-1", "f".repeat(64), MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20"), "CNY", MaintenanceBillingPostingStatus.POSTED, 2,
                quotedAt.plusMinutes(1));
        MaintenanceFundSettlementEvidence funds = new MaintenanceFundSettlementEvidence(
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.SUCCEEDED,
                "posting-1", null, "payment-1", "SUCCESS", new BigDecimal("20"), "CNY",
                null, null, quotedAt.plusMinutes(2));
        MaintenanceWorkflowOperation settlementOperation = MaintenanceWorkflowOperation.create(
                "operation-settlement", MaintenanceWorkflowAction.SETTLE_PREMIUM,
                "task-fee", funds.evidenceVersion(posting), funds.gateContentHash(posting),
                funds.status().getCode(), funds.detailSummary(), funds.recordedAt(), "settlement-service");
        MaintenanceWorkflowTask settled = quoted.settlePremium(posting, funds, settlementOperation);

        handler.on(new MaintenanceWorkflowTaskTransitionedEvent(
                MaintenanceId.of("case-1"), quoted, settled, null, null,
                settlementOperation.operationId(), settlementOperation.payloadHash(),
                settlementOperation.operatedAt(), settlementOperation.operatedBy(), "tenant-1"));

        assertEquals("posting-1", currentView.getBillingPostingId());
        assertEquals(MaintenanceBillingPostingStatus.POSTED, currentView.getBillingPostingStatus());
        assertEquals(2, currentView.getBillingCommissionAdjustmentCount());
        assertEquals(MaintenanceFundSettlementType.COLLECTION, currentView.getFundSettlementType());
        assertEquals(MaintenanceFundSettlementStatus.SUCCEEDED, currentView.getFundSettlementStatus());
        assertEquals("payment-1", currentView.getFundSettlementOrderId());
        assertEquals("SUCCESS", currentView.getFundSettlementExternalStatus());
        assertEquals(new BigDecimal("20"), currentView.getFundSettlementAmount());
        verify(repository).save(currentView);
    }

    @Test
    void shouldProjectEffectRequestAndPolicyApplicationEvidence() {
        MaintenanceWorkflowTaskViewRepository repository = mock(MaintenanceWorkflowTaskViewRepository.class);
        MaintenanceWorkflowProjectionEventHandler handler =
                new MaintenanceWorkflowProjectionEventHandler(repository);
        MaintenanceWorkflowTaskView currentView = view("task-effect");
        when(repository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "case-1", "task-effect"))
                .thenReturn(Optional.of(currentView));
        LocalDateTime requestedAt = LocalDateTime.parse("2026-08-25T14:00:00");
        MaintenanceWorkflowTask ready = new MaintenanceWorkflowTask(
                "task-effect", "POLICY_INFO_CHANGE", 0, 3,
                MaintenanceStepType.EFFECT, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.READY);
        MaintenanceEffectRequestEvidence request = new MaintenanceEffectRequestEvidence(
                "effect-request-1", "a".repeat(64), 7,
                EffectiveTimeType.IMMEDIATE, requestedAt, "b".repeat(64), requestedAt);
        MaintenanceWorkflowOperation requestOperation = MaintenanceWorkflowOperation.create(
                "operation-effect-request", MaintenanceWorkflowAction.REQUEST_EFFECT,
                "task-effect", request.evidenceVersion(), request.requestPayloadHash(),
                "EFFECTING", null, requestedAt, "operator-1");
        MaintenanceWorkflowTask requested = ready.requestEffect(request, requestOperation);

        handler.on(new MaintenanceWorkflowTaskTransitionedEvent(
                MaintenanceId.of("case-1"), ready, requested, null, null,
                requestOperation.operationId(), requestOperation.payloadHash(),
                requestOperation.operatedAt(), requestOperation.operatedBy(), "tenant-1"));

        assertEquals("effect-request-1", currentView.getEffectRequestId());
        assertEquals(7L, currentView.getEffectExpectedPolicyVersion());
        assertEquals(EffectiveTimeType.IMMEDIATE, currentView.getEffectTimeType());
        assertEquals(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL, currentView.getStatus());

        MaintenanceSnapshotReference appliedSnapshot = new MaintenanceSnapshotReference(
                "snapshot://case-1/applied", "c".repeat(64), 8,
                OffsetDateTime.parse("2026-08-25T14:01:00+08:00"));
        MaintenancePolicyApplicationEvidence application = new MaintenancePolicyApplicationEvidence(
                "effect-request-1", "END-20260825-001", 7, 8, "d".repeat(64), appliedSnapshot,
                List.of(new MaintenanceAppliedFieldEvidence(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        PolicyFieldDataType.TEXT, "13900000000")),
                requestedAt.plusMinutes(1));
        MaintenanceWorkflowOperation applicationOperation = MaintenanceWorkflowOperation.create(
                "operation-effect-applied", MaintenanceWorkflowAction.RECORD_POLICY_APPLICATION,
                "task-effect", application.evidenceVersion(), application.applicationHash(),
                "APPLIED", application.endorsementNo(), application.appliedAt(), "policy-service");
        MaintenanceWorkflowTask applied = requested.recordPolicyApplication(
                application, applicationOperation);

        handler.on(new MaintenanceWorkflowTaskTransitionedEvent(
                MaintenanceId.of("case-1"), requested, applied, null, null,
                applicationOperation.operationId(), applicationOperation.payloadHash(),
                applicationOperation.operatedAt(), applicationOperation.operatedBy(), "tenant-1"));

        assertEquals("END-20260825-001", currentView.getPolicyEndorsementNo());
        assertEquals(8L, currentView.getPolicyActualVersion());
        assertEquals("snapshot://case-1/applied", currentView.getAppliedSnapshotStorageKey());
        assertTrue(currentView.getAppliedFieldsJson().contains("policy.holder.mobile"));
        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, currentView.getStatus());
    }

    private MaintenanceWorkflowTaskView view(String taskId) {
        MaintenanceWorkflowTaskView view = new MaintenanceWorkflowTaskView();
        view.setTaskId(taskId);
        view.setMaintenanceId("case-1");
        view.setTenantId("tenant-1");
        return view;
    }

    private MaintenanceWorkflowTask task(
            String taskId,
            int sequence,
            MaintenanceWorkflowTaskStatus status) {
        MaintenanceWorkflowTask task = new MaintenanceWorkflowTask(
                taskId, "POLICY_INFO_CHANGE", 0, sequence,
                MaintenanceStepType.DATA_ENTRY, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.READY);
        if (status == MaintenanceWorkflowTaskStatus.PENDING) {
            return new MaintenanceWorkflowTask(
                    taskId, "POLICY_INFO_CHANGE", 0, sequence,
                    MaintenanceStepType.VALIDATION, MaintenanceStepMode.REQUIRED,
                    null, status);
        }
        MaintenanceWorkflowOperation claim = MaintenanceWorkflowOperation.create(
                "claim-" + taskId, MaintenanceWorkflowAction.CLAIM, taskId,
                null, null, null, null,
                LocalDateTime.parse("2026-08-25T11:00:00"), "operator-1");
        MaintenanceWorkflowOperation start = MaintenanceWorkflowOperation.create(
                "start-" + taskId, MaintenanceWorkflowAction.START, taskId,
                null, null, null, null,
                LocalDateTime.parse("2026-08-25T11:05:00"), "operator-1");
        return task.claim(claim).start(start);
    }
}
