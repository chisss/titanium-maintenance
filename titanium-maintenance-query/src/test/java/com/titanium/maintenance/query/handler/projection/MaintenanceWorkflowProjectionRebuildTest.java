package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.event.MaintenanceWorkflowInitializedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowTaskTransitionedEvent;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceBillingPostingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceFundSettlementEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePremiumQuoteEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowOperation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;

class MaintenanceWorkflowProjectionRebuildTest {

    private static final MaintenanceId ID = MaintenanceId.of("case-rebuild-1");
    private static final String FEE_TASK_ID = "case-rebuild-1:POLICY_INFO_CHANGE:FEE_SETTLEMENT";
    private static final String EFFECT_TASK_ID = "case-rebuild-1:POLICY_INFO_CHANGE:EFFECT";
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-08-25T12:00:00");

    private final Map<String, MaintenanceWorkflowTaskView> taskState = new LinkedHashMap<>();
    private MaintenanceWorkflowProjectionEventHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MaintenanceWorkflowTaskViewRepository repository = mock(MaintenanceWorkflowTaskViewRepository.class);
        when(repository.saveAll(any())).thenAnswer(invocation -> {
            List<MaintenanceWorkflowTaskView> views = invocation.getArgument(0);
            views.forEach(view -> taskState.put(view.getTaskId(), view));
            return views;
        });
        when(repository.findByTenantIdAndMaintenanceIdAndTaskId(
                anyString(), anyString(), anyString())).thenAnswer(invocation ->
                        Optional.ofNullable(taskState.get(invocation.getArgument(2))));
        when(repository.save(any(MaintenanceWorkflowTaskView.class))).thenAnswer(invocation -> {
            MaintenanceWorkflowTaskView view = invocation.getArgument(0);
            taskState.put(view.getTaskId(), view);
            return view;
        });
        handler = new MaintenanceWorkflowProjectionEventHandler(repository);
    }

    @Test
    void shouldRebuildQuotePostingFundsAndActivatedEffectFromWorkflowEvents() {
        MaintenanceWorkflowTask fee = task(
                FEE_TASK_ID, MaintenanceStepType.FEE_SETTLEMENT, 1,
                MaintenanceWorkflowTaskStatus.READY);
        MaintenanceWorkflowTask effect = task(
                EFFECT_TASK_ID, MaintenanceStepType.EFFECT, 2,
                MaintenanceWorkflowTaskStatus.PENDING);
        MaintenancePremiumQuoteEvidence quote = quote();
        MaintenanceWorkflowOperation quoteOperation = MaintenanceWorkflowOperation.create(
                "rebuild-quote", MaintenanceWorkflowAction.RECORD_PREMIUM_QUOTE, FEE_TASK_ID,
                quote.evidenceVersion(), quote.contentHash(), quote.status().getCode(),
                quote.detailSummary(), NOW.plusMinutes(1), "pricing-service");
        MaintenanceWorkflowTask quoted = fee.recordPremiumQuote(quote, quoteOperation);
        MaintenanceBillingPostingEvidence posting = posting();
        MaintenanceFundSettlementEvidence funds = funds();
        MaintenanceWorkflowOperation settlementOperation = MaintenanceWorkflowOperation.create(
                "rebuild-settlement", MaintenanceWorkflowAction.SETTLE_PREMIUM, FEE_TASK_ID,
                funds.evidenceVersion(posting), funds.gateContentHash(posting), funds.status().getCode(),
                funds.detailSummary(), NOW.plusMinutes(2), "settlement-service");
        MaintenanceWorkflowTask settled = quoted.settlePremium(posting, funds, settlementOperation);
        MaintenanceWorkflowTask activatedEffect = effect.activate(settlementOperation);

        handler.on(new MaintenanceWorkflowInitializedEvent(
                ID, List.of(fee, effect), NOW, "operator-1", "tenant-1"));
        handler.on(transition(fee, quoted, null, null, quoteOperation));
        handler.on(transition(quoted, settled, effect, activatedEffect, settlementOperation));

        MaintenanceWorkflowTaskView rebuiltFee = taskState.get(FEE_TASK_ID);
        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, rebuiltFee.getStatus());
        assertEquals("quote-1", rebuiltFee.getPremiumQuoteId());
        assertEquals("posting-1", rebuiltFee.getBillingPostingId());
        assertEquals(MaintenanceBillingPostingStatus.POSTED, rebuiltFee.getBillingPostingStatus());
        assertEquals("payment-1", rebuiltFee.getFundSettlementOrderId());
        assertEquals(MaintenanceFundSettlementStatus.SUCCEEDED, rebuiltFee.getFundSettlementStatus());
        assertEquals(MaintenanceWorkflowTaskStatus.READY, taskState.get(EFFECT_TASK_ID).getStatus());
    }

    private MaintenanceWorkflowTask task(
            String taskId,
            MaintenanceStepType stepType,
            int sequence,
            MaintenanceWorkflowTaskStatus status) {
        return new MaintenanceWorkflowTask(
                taskId, "POLICY_INFO_CHANGE", 0, sequence, stepType,
                MaintenanceStepMode.REQUIRED, null, status);
    }

    private MaintenancePremiumQuoteEvidence quote() {
        return new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.QUOTED, "quote-1", "a".repeat(64), "b".repeat(64),
                "original", "c".repeat(64), "replacement", "d".repeat(64),
                "plan-v2", "e".repeat(64), "f".repeat(64), "DEBIT 20 CNY; lines=1",
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "CNY",
                NOW, NOW.plusHours(24));
    }

    private MaintenanceBillingPostingEvidence posting() {
        return new MaintenanceBillingPostingEvidence(
                "posting-1", "quote-1", "f".repeat(64), MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20"), "CNY", MaintenanceBillingPostingStatus.POSTED,
                0, NOW.plusMinutes(1));
    }

    private MaintenanceFundSettlementEvidence funds() {
        return new MaintenanceFundSettlementEvidence(
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.SUCCEEDED,
                "posting-1", null, "payment-1", "SUCCESS",
                new BigDecimal("20"), "CNY", null, null, NOW.plusMinutes(2));
    }

    private MaintenanceWorkflowTaskTransitionedEvent transition(
            MaintenanceWorkflowTask before,
            MaintenanceWorkflowTask after,
            MaintenanceWorkflowTask activatedBefore,
            MaintenanceWorkflowTask activatedAfter,
            MaintenanceWorkflowOperation operation) {
        return new MaintenanceWorkflowTaskTransitionedEvent(
                ID, before, after, activatedBefore, activatedAfter,
                operation.operationId(), operation.payloadHash(), operation.operatedAt(),
                operation.operatedBy(), "tenant-1");
    }
}
