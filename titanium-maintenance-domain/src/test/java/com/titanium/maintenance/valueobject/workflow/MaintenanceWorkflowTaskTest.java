package com.titanium.maintenance.valueobject.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewGate;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

class MaintenanceWorkflowTaskTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-08-25T11:00:00");
    private static final String HASH = "a".repeat(64);

    @Test
    void shouldClaimStartAndCompleteDataEntryTask() {
        MaintenanceWorkflowTask ready = task(
                MaintenanceStepType.DATA_ENTRY, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY);

        MaintenanceWorkflowTask claimed = ready.claim(operation(
                "op-claim", MaintenanceWorkflowAction.CLAIM, null, null, null, null, "operator-1"));
        MaintenanceWorkflowTask started = claimed.start(operation(
                "op-start", MaintenanceWorkflowAction.START, null, null, null, null, "operator-1"));
        MaintenanceWorkflowTask completed = started.complete(operation(
                "op-complete", MaintenanceWorkflowAction.COMPLETE,
                null, null, "DATA_RECORDED", "录入完成", "operator-1"));

        assertEquals("operator-1", claimed.assignment().assignee());
        assertEquals(MaintenanceWorkflowTaskStatus.IN_PROGRESS, started.status());
        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, completed.status());
        assertEquals("DATA_RECORDED", completed.lastOperation().resultCode());
    }

    @Test
    void shouldFailAndRetryTaskWithFreshAssignment() {
        MaintenanceWorkflowTask started = task(
                        MaintenanceStepType.DATA_ENTRY, MaintenanceStepMode.REQUIRED,
                        MaintenanceWorkflowTaskStatus.READY)
                .claim(operation("op-claim", MaintenanceWorkflowAction.CLAIM,
                        null, null, null, null, "operator-1"))
                .start(operation("op-start", MaintenanceWorkflowAction.START,
                        null, null, null, null, "operator-1"));

        MaintenanceWorkflowTask failed = started.fail(operation(
                "op-fail", MaintenanceWorkflowAction.FAIL,
                null, null, "REMOTE_TIMEOUT", "权威端超时", "operator-1"));
        MaintenanceWorkflowTask retried = failed.retry(operation(
                "op-retry", MaintenanceWorkflowAction.RETRY,
                null, null, null, "重新取得权威证据", "supervisor-1"));

        assertEquals(MaintenanceWorkflowTaskStatus.FAILED, failed.status());
        assertEquals("REMOTE_TIMEOUT", failed.failure().failureCode());
        assertEquals(MaintenanceWorkflowTaskStatus.READY, retried.status());
        assertEquals(1, retried.retryCount());
        assertNull(retried.assignment());
        assertNull(retried.failure());
    }

    @Test
    void shouldRecordVersionedConditionDecision() {
        MaintenanceWorkflowTask waiting = new MaintenanceWorkflowTask(
                "case-1:ITEM:REVIEW", "ITEM", 0, 2,
                MaintenanceStepType.REVIEW, MaintenanceStepMode.CONDITIONAL,
                "review-rule", MaintenanceWorkflowTaskStatus.WAITING_CONDITION);
        MaintenanceWorkflowOperation operation = operation(
                "op-condition", MaintenanceWorkflowAction.DECIDE_CONDITION,
                "rule-v3", HASH, MaintenanceWorkflowConditionDecision.SKIP.getCode(),
                "低风险案件无需审核", "rule-engine");

        MaintenanceWorkflowTask skipped = waiting.decideCondition(
                MaintenanceWorkflowConditionDecision.SKIP, operation);

        assertEquals(MaintenanceWorkflowTaskStatus.SKIPPED, skipped.status());
        assertEquals("rule-v3", skipped.conditionEvidence().ruleVersion());
        assertEquals(HASH, skipped.conditionEvidence().inputHash());
    }

    @Test
    void shouldRejectOperatorOtherThanAssignee() {
        MaintenanceWorkflowTask claimed = task(
                        MaintenanceStepType.DATA_ENTRY, MaintenanceStepMode.REQUIRED,
                        MaintenanceWorkflowTaskStatus.READY)
                .claim(operation("op-claim", MaintenanceWorkflowAction.CLAIM,
                        null, null, null, null, "operator-1"));

        assertThrows(MaintenanceValidationException.class, () -> claimed.start(operation(
                "op-start", MaintenanceWorkflowAction.START,
                null, null, null, null, "operator-2")));
    }

    @Test
    void shouldRequireDedicatedCommandForReviewCompletion() {
        MaintenanceWorkflowTask review = task(
                        MaintenanceStepType.REVIEW, MaintenanceStepMode.REQUIRED,
                        MaintenanceWorkflowTaskStatus.READY)
                .claim(operation("op-claim", MaintenanceWorkflowAction.CLAIM,
                        null, null, null, null, "operator-1"))
                .start(operation("op-start", MaintenanceWorkflowAction.START,
                        null, null, null, null, "operator-1"));

        assertThrows(MaintenanceValidationException.class, () -> review.complete(operation(
                "op-complete", MaintenanceWorkflowAction.COMPLETE,
                null, null, "APPROVED", "审核通过", "operator-1")));
    }

    @Test
    void shouldAllowCurrentAssigneeToApproveOrRejectManualReview() {
        MaintenanceWorkflowTask started = task(
                        MaintenanceStepType.REVIEW, MaintenanceStepMode.REQUIRED,
                        MaintenanceWorkflowTaskStatus.READY)
                .claim(operation("op-claim", MaintenanceWorkflowAction.CLAIM,
                        null, null, null, null, "reviewer-1"))
                .start(operation("op-start", MaintenanceWorkflowAction.START,
                        null, null, null, null, "reviewer-1"));

        MaintenanceWorkflowReviewEvidence approval = manualEvidence(
                MaintenanceReviewDecision.APPROVE, "审核通过", "reviewer-1");
        MaintenanceWorkflowReviewEvidence rejection = manualEvidence(
                MaintenanceReviewDecision.REJECT, "身份材料不一致", "reviewer-1");

        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED,
                started.decideReview(approval, reviewOperation("op-approve", approval)).status());
        assertEquals(MaintenanceWorkflowTaskStatus.REJECTED,
                started.decideReview(rejection, reviewOperation("op-reject", rejection)).status());
    }

    @Test
    void shouldRequireAllSevenGatesForAutomaticReviewAndRejectClaimedTask() {
        MaintenanceWorkflowTask ready = task(
                MaintenanceStepType.REVIEW, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY);
        MaintenanceWorkflowReviewEvidence evidence = automaticEvidence();

        MaintenanceWorkflowTask completed = ready.decideReview(
                evidence, reviewOperation("op-auto", evidence));

        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, completed.status());
        assertEquals(7, completed.reviewEvidence().gates().size());

        MaintenanceWorkflowTask claimed = ready.claim(operation(
                "op-claim", MaintenanceWorkflowAction.CLAIM,
                null, null, null, null, "reviewer-1"));
        assertThrows(MaintenanceValidationException.class,
                () -> claimed.decideReview(evidence, reviewOperation("op-auto", evidence)));
        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenanceWorkflowReviewEvidence(
                        MaintenanceReviewMode.AUTOMATIC, MaintenanceReviewDecision.REJECT,
                        "APPROVAL_STANDARD", "policy-v1", automaticGates(), "拒绝",
                        NOW, "review-engine"));
    }

    @Test
    void shouldCompleteRejectOrWaitForExternalUnderwriting() {
        MaintenanceWorkflowTask ready = task(
                MaintenanceStepType.UNDERWRITING, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY);
        MaintenanceUnderwritingEvidence approval = underwritingEvidence(
                MaintenanceUnderwritingConclusion.APPROVED, List.of(), NOW);
        MaintenanceUnderwritingEvidence rejection = underwritingEvidence(
                MaintenanceUnderwritingConclusion.REJECTED, List.of(), NOW);
        MaintenanceUnderwritingEvidence manual = underwritingEvidence(
                MaintenanceUnderwritingConclusion.MANUAL_REVIEW, List.of(), null);

        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED,
                ready.decideUnderwriting(approval, underwritingOperation("uw-approve", approval)).status());
        assertEquals(MaintenanceWorkflowTaskStatus.REJECTED,
                ready.decideUnderwriting(rejection, underwritingOperation("uw-reject", rejection)).status());
        MaintenanceWorkflowTask waiting = ready.decideUnderwriting(
                manual, underwritingOperation("uw-manual", manual));
        assertEquals(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL, waiting.status());
        assertEquals("underwriting-1", waiting.underwritingEvidence().underwritingCaseId());
    }

    @Test
    void shouldFreezeConditionalUnderwritingAndRefreshManualResult() {
        MaintenanceWorkflowTask ready = task(
                MaintenanceStepType.UNDERWRITING, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY);
        MaintenanceUnderwritingEvidence manual = underwritingEvidence(
                MaintenanceUnderwritingConclusion.MANUAL_REVIEW, List.of(), null);
        MaintenanceWorkflowTask waiting = ready.decideUnderwriting(
                manual, underwritingOperation("uw-manual", manual));
        MaintenanceUnderwritingEvidence conditional = underwritingEvidence(
                MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED,
                List.of("REVIEW_FIELD:insured.occupation"), NOW);

        MaintenanceWorkflowTask completed = waiting.decideUnderwriting(
                conditional, underwritingOperation("uw-refresh", conditional));

        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, completed.status());
        assertEquals(List.of("REVIEW_FIELD:insured.occupation"),
                completed.underwritingEvidence().additionalConditions());
    }

    @Test
    void shouldRequireSkippedConfigurationForNotRequiredConclusion() {
        MaintenanceUnderwritingEvidence notRequired = underwritingEvidence(
                MaintenanceUnderwritingConclusion.NOT_REQUIRED, List.of(), NOW);
        MaintenanceWorkflowTask skipped = task(
                MaintenanceStepType.UNDERWRITING, MaintenanceStepMode.SKIPPED,
                MaintenanceWorkflowTaskStatus.SKIPPED);
        MaintenanceWorkflowTask required = task(
                MaintenanceStepType.UNDERWRITING, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY);

        assertEquals(MaintenanceWorkflowTaskStatus.SKIPPED,
                skipped.decideUnderwriting(
                        notRequired, underwritingOperation("uw-not-required", notRequired)).status());
        assertThrows(MaintenanceValidationException.class,
                () -> required.decideUnderwriting(
                        notRequired, underwritingOperation("uw-invalid", notRequired)));
    }

    @Test
    void shouldRecordQuoteWithoutCompletingFeeTask() {
        MaintenanceWorkflowTask ready = task(
                MaintenanceStepType.FEE_SETTLEMENT, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY);
        MaintenancePremiumQuoteEvidence evidence = quoteEvidence();

        MaintenanceWorkflowTask quoted = ready.recordPremiumQuote(
                evidence, quoteOperation("quote-1", evidence));
        MaintenancePremiumQuoteEvidence refreshedEvidence = new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.QUOTED, "quote-2", "d".repeat(64), "e".repeat(64),
                "original", "f".repeat(64), "replacement-2", "1".repeat(64),
                "plan-v2", "2".repeat(64), "3".repeat(64), "DEBIT 20 CNY; lines=1",
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "CNY",
                NOW.plusMinutes(1), NOW.plusHours(24));
        MaintenanceWorkflowTask refreshed = quoted.recordPremiumQuote(
                refreshedEvidence, quoteOperation("quote-2", refreshedEvidence));

        assertEquals(MaintenanceWorkflowTaskStatus.QUOTED, quoted.status());
        assertEquals("quote-1", quoted.premiumQuoteEvidence().quoteId());
        assertEquals("quote-2", refreshed.premiumQuoteEvidence().quoteId());
    }

    @Test
    void shouldOnlyRecordNotRequiredForConfiguredOrConditionalSkip() {
        MaintenancePremiumQuoteEvidence evidence = MaintenancePremiumQuoteEvidence.notRequired(
                "冻结配置为无费用", NOW);
        MaintenanceWorkflowTask configuredSkip = task(
                MaintenanceStepType.FEE_SETTLEMENT, MaintenanceStepMode.SKIPPED,
                MaintenanceWorkflowTaskStatus.SKIPPED);
        MaintenanceWorkflowTask required = task(
                MaintenanceStepType.FEE_SETTLEMENT, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY);

        MaintenanceWorkflowTask recorded = configuredSkip.recordPremiumQuote(
                evidence, quoteOperation("quote-not-required", evidence));

        assertEquals(MaintenanceWorkflowTaskStatus.SKIPPED, recorded.status());
        assertEquals(MaintenancePremiumQuoteStatus.NOT_REQUIRED,
                recorded.premiumQuoteEvidence().status());
        assertThrows(MaintenanceValidationException.class,
                () -> required.recordPremiumQuote(
                        evidence, quoteOperation("quote-invalid", evidence)));
    }

    @Test
    void shouldWaitForCollectionAndCompleteOnlyAfterPaymentSuccess() {
        MaintenanceWorkflowTask quoted = quotedFeeTask();
        MaintenanceBillingPostingEvidence posting = posting(
                MaintenanceBillingPostingStatus.POSTED, MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20"));
        MaintenanceFundSettlementEvidence pending = funds(
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.PENDING,
                "payment-1", "PENDING", new BigDecimal("20"), null, null);
        MaintenanceWorkflowTask waiting = quoted.settlePremium(
                posting, pending, settlementOperation("settle-pending", posting, pending));
        MaintenanceFundSettlementEvidence succeeded = funds(
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.SUCCEEDED,
                "payment-1", "SUCCESS", new BigDecimal("20"), null, null);

        MaintenanceWorkflowTask completed = waiting.settlePremium(
                posting, succeeded, settlementOperation("settle-success", posting, succeeded));

        assertEquals(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL, waiting.status());
        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, completed.status());
        assertEquals("payment-1", completed.fundSettlementEvidence().orderId());
    }

    @Test
    void shouldKeepPostingCheckpointWhenPaymentFailsAndTaskRetries() {
        MaintenanceWorkflowTask quoted = quotedFeeTask();
        MaintenanceBillingPostingEvidence posting = posting(
                MaintenanceBillingPostingStatus.POSTED, MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20"));
        MaintenanceFundSettlementEvidence failedFunds = funds(
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.FAILED,
                "payment-1", "FAILED", new BigDecimal("20"),
                "PAYMENT_FAILED", "渠道确认收款失败");

        MaintenanceWorkflowTask failed = quoted.settlePremium(
                posting, failedFunds, settlementOperation("settle-failed", posting, failedFunds));
        MaintenanceWorkflowTask retried = failed.retry(operation(
                "retry-payment", MaintenanceWorkflowAction.RETRY,
                null, null, null, "创建新收款尝试", "operator-1"));

        assertEquals(MaintenanceWorkflowTaskStatus.FAILED, failed.status());
        assertEquals("PAYMENT_FAILED", failed.failure().failureCode());
        assertEquals(MaintenanceWorkflowTaskStatus.READY, retried.status());
        assertEquals(posting, retried.billingPostingEvidence());
        assertEquals(failedFunds, retried.fundSettlementEvidence());
    }

    @Test
    void shouldRejectReversedPostingEvenWhenFundEvidenceClaimsSuccess() {
        MaintenanceWorkflowTask quoted = quotedFeeTask();
        MaintenanceBillingPostingEvidence reversed = posting(
                MaintenanceBillingPostingStatus.REVERSED, MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20"));
        MaintenanceFundSettlementEvidence succeeded = funds(
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.SUCCEEDED,
                "payment-1", "SUCCESS", new BigDecimal("20"), null, null);

        MaintenanceWorkflowTask failed = quoted.settlePremium(
                reversed, succeeded, settlementOperation("settle-reversed", reversed, succeeded));

        assertEquals(MaintenanceWorkflowTaskStatus.FAILED, failed.status());
        assertEquals("BILLING_POSTING_REVERSED", failed.failure().failureCode());
    }

    @Test
    void shouldProduceStableOperationHashAndDetectPayloadDifference() {
        MaintenanceWorkflowOperation first = operation(
                "operation-1", MaintenanceWorkflowAction.COMPLETE,
                "evidence-v1", HASH, "PASSED", "校验通过", "operator-1");
        MaintenanceWorkflowOperation retry = operation(
                "operation-1", MaintenanceWorkflowAction.COMPLETE,
                "evidence-v1", HASH, "PASSED", "校验通过", "operator-1");
        MaintenanceWorkflowOperation conflict = operation(
                "operation-1", MaintenanceWorkflowAction.COMPLETE,
                "evidence-v1", HASH, "REJECTED", "校验失败", "operator-1");

        assertEquals(first.payloadHash(), retry.payloadHash());
        assertNotEquals(first.payloadHash(), conflict.payloadHash());
    }

    private MaintenanceWorkflowTask task(
            MaintenanceStepType stepType,
            MaintenanceStepMode mode,
            MaintenanceWorkflowTaskStatus status) {
        return new MaintenanceWorkflowTask(
                "case-1:ITEM:" + stepType.getCode(), "ITEM", 0, 2,
                stepType, mode, mode == MaintenanceStepMode.CONDITIONAL ? "condition-rule" : null,
                status);
    }

    private MaintenanceWorkflowOperation operation(
            String operationId,
            MaintenanceWorkflowAction action,
            String evidenceVersion,
            String evidenceHash,
            String resultCode,
            String reason,
            String operatorId) {
        return MaintenanceWorkflowOperation.create(
                operationId, action, "case-1:ITEM:DATA_ENTRY",
                evidenceVersion, evidenceHash, resultCode, reason, NOW, operatorId);
    }

    private MaintenanceWorkflowOperation reviewOperation(
            String operationId,
            MaintenanceWorkflowReviewEvidence evidence) {
        return operation(operationId, MaintenanceWorkflowAction.DECIDE_REVIEW,
                evidence.policyVersion(), evidence.contentHash(), evidence.decision().getCode(),
                evidence.comment(), evidence.decidedBy());
    }

    private MaintenanceWorkflowReviewEvidence manualEvidence(
            MaintenanceReviewDecision decision,
            String comment,
            String reviewer) {
        return new MaintenanceWorkflowReviewEvidence(
                MaintenanceReviewMode.MANUAL, decision, "APPROVAL_STANDARD", "policy-v1",
                List.of(), comment, NOW, reviewer);
    }

    private MaintenanceWorkflowReviewEvidence automaticEvidence() {
        return new MaintenanceWorkflowReviewEvidence(
                MaintenanceReviewMode.AUTOMATIC, MaintenanceReviewDecision.APPROVE,
                "APPROVAL_STANDARD", "policy-v1", automaticGates(),
                "七类自动审核门禁全部通过", NOW, "review-engine");
    }

    private MaintenanceUnderwritingEvidence underwritingEvidence(
            MaintenanceUnderwritingConclusion conclusion,
            List<String> conditions,
            LocalDateTime completedAt) {
        return new MaintenanceUnderwritingEvidence(
                "underwriting-1", "b".repeat(64), "rule-v1", "model-v1",
                conclusion, conditions, "核保结论摘要", completedAt);
    }

    private MaintenanceWorkflowOperation underwritingOperation(
            String operationId,
            MaintenanceUnderwritingEvidence evidence) {
        return operation(operationId, MaintenanceWorkflowAction.DECIDE_UNDERWRITING,
                evidence.ruleVersion(), evidence.contentHash(), evidence.conclusion().getCode(),
                evidence.summary(), "underwriting-service");
    }

    private MaintenancePremiumQuoteEvidence quoteEvidence() {
        return new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.QUOTED, "quote-1", "a".repeat(64), "b".repeat(64),
                "original", "c".repeat(64), "replacement", "d".repeat(64),
                "plan-v2", "e".repeat(64), "f".repeat(64), "DEBIT 20 CNY; lines=1",
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "CNY",
                NOW, NOW.plusHours(24));
    }

    private MaintenanceWorkflowTask quotedFeeTask() {
        MaintenanceWorkflowTask ready = task(
                MaintenanceStepType.FEE_SETTLEMENT, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY);
        MaintenancePremiumQuoteEvidence evidence = quoteEvidence();
        return ready.recordPremiumQuote(evidence, quoteOperation("quote-before-settlement", evidence));
    }

    private MaintenanceBillingPostingEvidence posting(
            MaintenanceBillingPostingStatus status,
            MaintenanceBalanceDirection direction,
            BigDecimal amount) {
        return new MaintenanceBillingPostingEvidence(
                "posting-1", "quote-1", "f".repeat(64), direction, amount,
                "CNY", status, 0, NOW.plusMinutes(2));
    }

    private MaintenanceFundSettlementEvidence funds(
            MaintenanceFundSettlementType type,
            MaintenanceFundSettlementStatus status,
            String orderId,
            String externalStatus,
            BigDecimal amount,
            String failureCode,
            String failureMessage) {
        return new MaintenanceFundSettlementEvidence(
                type, status, "posting-1", null, orderId, externalStatus,
                amount, "CNY", failureCode, failureMessage, NOW.plusMinutes(3));
    }

    private MaintenanceWorkflowOperation settlementOperation(
            String operationId,
            MaintenanceBillingPostingEvidence posting,
            MaintenanceFundSettlementEvidence funds) {
        String resultCode = posting.status() == MaintenanceBillingPostingStatus.REVERSED
                ? posting.status().getCode()
                : funds.status().getCode();
        return operation(operationId, MaintenanceWorkflowAction.SETTLE_PREMIUM,
                funds.evidenceVersion(posting), funds.gateContentHash(posting), resultCode,
                funds.detailSummary(), "settlement-service");
    }

    private MaintenanceWorkflowOperation quoteOperation(
            String operationId,
            MaintenancePremiumQuoteEvidence evidence) {
        return operation(operationId, MaintenanceWorkflowAction.RECORD_PREMIUM_QUOTE,
                evidence.evidenceVersion(), evidence.contentHash(), evidence.status().getCode(),
                evidence.detailSummary(), "pricing-service");
    }

    private List<MaintenanceReviewGateEvidence> automaticGates() {
        return Arrays.stream(MaintenanceReviewGate.values())
                .map(gate -> new MaintenanceReviewGateEvidence(
                        gate, true, HASH, gate.getCode() + "_PASSED"))
                .toList();
    }
}
