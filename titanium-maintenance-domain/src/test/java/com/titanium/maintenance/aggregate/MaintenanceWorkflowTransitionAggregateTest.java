package com.titanium.maintenance.aggregate;

import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.axonframework.test.matchers.Matchers.payloadsMatching;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.command.ClaimMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.CompleteMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.CompleteMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.DecideMaintenanceReviewCommand;
import com.titanium.maintenance.command.DecideMaintenanceUnderwritingCommand;
import com.titanium.maintenance.command.DecideMaintenanceWorkflowConditionCommand;
import com.titanium.maintenance.command.FailMaintenanceEffectCommand;
import com.titanium.maintenance.command.PauseMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.RecordMaintenanceCasePolicyApplicationCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectCompensationCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectScheduleAttemptCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectScheduleFailureCommand;
import com.titanium.maintenance.command.RecordMaintenancePolicyApplicationCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumQuoteCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumSettlementCommand;
import com.titanium.maintenance.command.RequestMaintenanceCaseEffectCommand;
import com.titanium.maintenance.command.RequestMaintenanceEffectCommand;
import com.titanium.maintenance.command.ResumeMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.ScheduleMaintenanceEffectCommand;
import com.titanium.maintenance.command.StartMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
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
import com.titanium.maintenance.common.exception.MaintenanceConflictException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.event.MaintenanceCaseInitializationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceCaseRejectedByReviewEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceEffectCompensationRequiredEvent;
import com.titanium.maintenance.event.MaintenanceEffectCompensationResolvedEvent;
import com.titanium.maintenance.event.MaintenanceEffectStatusChangedEvent;
import com.titanium.maintenance.event.MaintenanceFieldChangesRecordedEvent;
import com.titanium.maintenance.event.MaintenanceItemAddedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowInitializedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowTaskTransitionedEvent;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.maintenance.valueobject.workflow.MaintenanceAppliedFieldEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceBillingPostingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectCompensationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceFundSettlementEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyApplicationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePremiumQuoteEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceReviewGateEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceUnderwritingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowOperation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowReviewEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

class MaintenanceWorkflowTransitionAggregateTest {

    private static final MaintenanceId ID = MaintenanceId.of("workflow-case-1");
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-08-25T11:00:00");
    private static final String ITEM_CODE = "POLICY_INFO_CHANGE";
    private static final String DATA_TASK_ID = "workflow-case-1:POLICY_INFO_CHANGE:DATA_ENTRY";
    private static final String VALIDATION_TASK_ID = "workflow-case-1:POLICY_INFO_CHANGE:VALIDATION";
    private static final String REVIEW_TASK_ID = "workflow-case-1:POLICY_INFO_CHANGE:REVIEW";
    private static final String UNDERWRITING_TASK_ID = "workflow-case-1:POLICY_INFO_CHANGE:UNDERWRITING";
    private static final String FEE_TASK_ID = "workflow-case-1:POLICY_INFO_CHANGE:FEE_SETTLEMENT";
    private static final String EFFECT_TASK_ID = "workflow-case-1:POLICY_INFO_CHANGE:EFFECT";
    private static final String SECOND_EFFECT_TASK_ID = "workflow-case-1:BENEFICIARY_CHANGE:EFFECT";

    private FixtureConfiguration<Maintenance> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Maintenance.class);
    }

    @Test
    void shouldClaimReadyTask() {
        fixture.given(baseEvents())
                .when(new ClaimMaintenanceWorkflowTaskCommand(
                        ID, DATA_TASK_ID, "operation-claim", "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(instanceOf(
                        MaintenanceWorkflowTaskTransitionedEvent.class))))
                .expectState(aggregate -> {
                    MaintenanceWorkflowTask task = aggregate.getWorkflowTasks().getFirst();
                    assertEquals(MaintenanceWorkflowTaskStatus.READY, task.status());
                    assertEquals("operator-1", task.assignment().assignee());
                });
    }

    @Test
    void shouldCompleteTaskAndActivateNextTaskInSameItem() {
        MaintenanceWorkflowTask dataEntry = dataEntryTask();
        MaintenanceWorkflowOperation claim = operation(
                "operation-claim", MaintenanceWorkflowAction.CLAIM, DATA_TASK_ID,
                null, null, null, null, "operator-1");
        MaintenanceWorkflowTask claimed = dataEntry.claim(claim);
        MaintenanceWorkflowOperation start = operation(
                "operation-start", MaintenanceWorkflowAction.START, DATA_TASK_ID,
                null, null, null, null, "operator-1");
        MaintenanceWorkflowTask started = claimed.start(start);

        fixture.given(createdEvent(), itemAddedEvent(ITEM_CODE, "policy.holder.mobile"),
                        fieldChangesRecordedEvent(ITEM_CODE, "policy-1", "policy.holder.mobile"),
                        initializedEvent(), workflowInitializedEvent(),
                        transition(dataEntry, claimed, null, null, claim),
                        transition(claimed, started, null, null, start))
                .when(new CompleteMaintenanceWorkflowTaskCommand(
                        ID, DATA_TASK_ID, "operation-complete",
                        null, null, "DATA_RECORDED", "录入完成", "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(instanceOf(
                        MaintenanceWorkflowTaskTransitionedEvent.class))))
                .expectState(aggregate -> {
                    assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED,
                            aggregate.getWorkflowTasks().getFirst().status());
                    assertEquals(MaintenanceWorkflowTaskStatus.READY,
                            aggregate.getWorkflowTasks().get(1).status());
                });
    }

    @Test
    void shouldRejectCompletingDataEntryWithoutActualFieldChanges() {
        MaintenanceWorkflowTask dataEntry = dataEntryTask();
        MaintenanceWorkflowOperation claim = operation(
                "operation-claim-empty", MaintenanceWorkflowAction.CLAIM, DATA_TASK_ID,
                null, null, null, null, "operator-1");
        MaintenanceWorkflowTask claimed = dataEntry.claim(claim);
        MaintenanceWorkflowOperation start = operation(
                "operation-start-empty", MaintenanceWorkflowAction.START, DATA_TASK_ID,
                null, null, null, null, "operator-1");
        MaintenanceWorkflowTask started = claimed.start(start);

        fixture.given(createdEvent(), itemAddedEvent(ITEM_CODE, "policy.holder.mobile"),
                        initializedEvent(), workflowInitializedEvent(),
                        transition(dataEntry, claimed, null, null, claim),
                        transition(claimed, started, null, null, start))
                .when(new CompleteMaintenanceWorkflowTaskCommand(
                        ID, DATA_TASK_ID, "operation-complete-empty",
                        null, null, "DATA_RECORDED", "录入完成", "operator-1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldSkipConditionalTaskAndActivateNextTask() {
        MaintenanceWorkflowTask condition = new MaintenanceWorkflowTask(
                "workflow-case-1:POLICY_INFO_CHANGE:REVIEW", ITEM_CODE, 0, 1,
                MaintenanceStepType.REVIEW, MaintenanceStepMode.CONDITIONAL,
                "review-rule", MaintenanceWorkflowTaskStatus.WAITING_CONDITION);
        MaintenanceWorkflowTask next = new MaintenanceWorkflowTask(
                "workflow-case-1:POLICY_INFO_CHANGE:EFFECT", ITEM_CODE, 0, 2,
                MaintenanceStepType.EFFECT, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.PENDING);

        fixture.given(createdEvent(), initializedEvent(), new MaintenanceWorkflowInitializedEvent(
                        ID, List.of(condition, next), NOW, "operator-1", "tenant-1"))
                .when(new DecideMaintenanceWorkflowConditionCommand(
                        ID, condition.taskId(), "operation-condition", "rule-v2", "a".repeat(64),
                        MaintenanceWorkflowConditionDecision.SKIP, "低风险无需审核", "rule-engine"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(MaintenanceWorkflowTaskStatus.SKIPPED,
                            aggregate.getWorkflowTasks().getFirst().status());
                    assertEquals(MaintenanceWorkflowTaskStatus.READY,
                            aggregate.getWorkflowTasks().get(1).status());
                });
    }

    @Test
    void shouldRecordQuoteWithoutActivatingEffectTask() {
        MaintenanceWorkflowTask feeTask = new MaintenanceWorkflowTask(
                FEE_TASK_ID, ITEM_CODE, 0, 1, MaintenanceStepType.FEE_SETTLEMENT,
                MaintenanceStepMode.REQUIRED, null, MaintenanceWorkflowTaskStatus.READY);
        MaintenanceWorkflowTask effectTask = new MaintenanceWorkflowTask(
                EFFECT_TASK_ID, ITEM_CODE, 0, 2,
                MaintenanceStepType.EFFECT, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.PENDING);
        MaintenancePremiumQuoteEvidence evidence = new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.QUOTED, "quote-1", "a".repeat(64), "b".repeat(64),
                "original", "c".repeat(64), "replacement", "d".repeat(64),
                "plan-v2", "e".repeat(64), "f".repeat(64), "DEBIT 20 CNY; lines=1",
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "CNY",
                NOW, NOW.plusHours(24));

        fixture.given(createdEvent(), initializedEvent(), new MaintenanceWorkflowInitializedEvent(
                ID, List.of(feeTask, effectTask), NOW, "operator-1", "tenant-1"))
                .when(new RecordMaintenancePremiumQuoteCommand(
                        ID, FEE_TASK_ID, "operation-quote", evidence, "pricing-service"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(MaintenanceWorkflowTaskStatus.QUOTED,
                            aggregate.getWorkflowTasks().getFirst().status());
                    assertEquals(MaintenanceWorkflowTaskStatus.PENDING,
                            aggregate.getWorkflowTasks().get(1).status());
                });
    }

    @Test
    void shouldActivateEffectOnlyAfterSuccessfulFundSettlement() {
        MaintenanceBillingPostingEvidence posting = posting(MaintenanceBillingPostingStatus.POSTED);
        MaintenanceFundSettlementEvidence funds = funds(MaintenanceFundSettlementStatus.SUCCEEDED);

        fixture.given(quotedFeeWorkflowEvents())
                .when(new RecordMaintenancePremiumSettlementCommand(
                        ID, FEE_TASK_ID, "operation-settlement-success", posting, funds,
                        "settlement-service"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED,
                            aggregate.getWorkflowTasks().getFirst().status());
                    assertEquals(MaintenanceWorkflowTaskStatus.READY,
                            aggregate.getWorkflowTasks().get(1).status());
                });
    }

    @Test
    void shouldCompleteAuthoritativePhaseFourWorkflowBeforeActivatingEffect() {
        MaintenanceWorkflowReviewEvidence reviewEvidence = automaticReviewEvidence();
        MaintenanceUnderwritingEvidence underwritingEvidence = approvedUnderwritingEvidence();
        MaintenanceBillingPostingEvidence posting = posting(MaintenanceBillingPostingStatus.POSTED);
        MaintenanceFundSettlementEvidence funds = funds(MaintenanceFundSettlementStatus.SUCCEEDED);

        fixture.given(createdEvent(), itemAddedEvent(ITEM_CODE, "policy.holder.mobile"),
                        fieldChangesRecordedEvent(ITEM_CODE, "policy-1", "policy.holder.mobile"),
                        initializedEvent(), phaseFourWorkflowInitializedEvent())
                .andGivenCommands(
                        new ClaimMaintenanceWorkflowTaskCommand(
                                ID, DATA_TASK_ID, "phase4-claim-data", "operator-1"),
                        new StartMaintenanceWorkflowTaskCommand(
                                ID, DATA_TASK_ID, "phase4-start-data", "operator-1"),
                        new CompleteMaintenanceWorkflowTaskCommand(
                                ID, DATA_TASK_ID, "phase4-complete-data",
                                null, null, "DATA_RECORDED", "信息录入完成", "operator-1"),
                        new DecideMaintenanceWorkflowConditionCommand(
                                ID, REVIEW_TASK_ID, "phase4-review-condition", "rule-v1", "a".repeat(64),
                                MaintenanceWorkflowConditionDecision.EXECUTE, "案件需要自动审核", "rule-engine"),
                        new DecideMaintenanceReviewCommand(
                                ID, REVIEW_TASK_ID, "phase4-auto-review", reviewEvidence, "review-engine"),
                        new DecideMaintenanceUnderwritingCommand(
                                ID, UNDERWRITING_TASK_ID, "phase4-underwriting",
                                underwritingEvidence, "underwriting-service"),
                        new RecordMaintenancePremiumQuoteCommand(
                                ID, FEE_TASK_ID, "phase4-premium-quote", quoteEvidence(), "pricing-service"))
                .when(new RecordMaintenancePremiumSettlementCommand(
                        ID, FEE_TASK_ID, "phase4-premium-settlement", posting, funds,
                        "settlement-service"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    List<MaintenanceWorkflowTask> tasks = aggregate.getWorkflowTasks();
                    assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, tasks.get(0).status());
                    assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, tasks.get(1).status());
                    assertEquals(MaintenanceWorkflowConditionDecision.EXECUTE,
                            tasks.get(1).conditionEvidence().decision());
                    assertEquals(MaintenanceReviewMode.AUTOMATIC, tasks.get(1).reviewEvidence().mode());
                    assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, tasks.get(2).status());
                    assertEquals(MaintenanceUnderwritingConclusion.APPROVED,
                            tasks.get(2).underwritingEvidence().conclusion());
                    assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, tasks.get(3).status());
                    assertEquals(MaintenanceBillingPostingStatus.POSTED,
                            tasks.get(3).billingPostingEvidence().status());
                    assertEquals(MaintenanceFundSettlementStatus.SUCCEEDED,
                            tasks.get(3).fundSettlementEvidence().status());
                    assertEquals(MaintenanceWorkflowTaskStatus.READY, tasks.get(4).status());
                });
    }

    @Test
    void shouldAdvanceInterleavedItemsWithoutCrossActivatingTasks() {
        String secondItem = "INSURED_INFO_CHANGE";
        String secondDataTaskId = "workflow-case-1:INSURED_INFO_CHANGE:DATA_ENTRY";
        String secondValidationTaskId = "workflow-case-1:INSURED_INFO_CHANGE:VALIDATION";
        List<MaintenanceWorkflowTask> tasks = List.of(
                dataEntryTask(),
                validationTask(),
                new MaintenanceWorkflowTask(
                        secondDataTaskId, secondItem, 1, 1,
                        MaintenanceStepType.DATA_ENTRY, MaintenanceStepMode.REQUIRED,
                        null, MaintenanceWorkflowTaskStatus.READY),
                new MaintenanceWorkflowTask(
                        secondValidationTaskId, secondItem, 1, 2,
                        MaintenanceStepType.VALIDATION, MaintenanceStepMode.REQUIRED,
                        null, MaintenanceWorkflowTaskStatus.PENDING));

        fixture.given(createdEvent(),
                        itemAddedEvent(ITEM_CODE, "policy.holder.mobile"),
                        fieldChangesRecordedEvent(ITEM_CODE, "policy-1", "policy.holder.mobile"),
                        itemAddedEvent(secondItem, "policy.insured.mobile"),
                        fieldChangesRecordedEvent(secondItem, "insured-1", "policy.insured.mobile"),
                        new MaintenanceCaseInitializationCompletedEvent(
                                ID, List.of(ITEM_CODE, secondItem), NOW, "operator-1", "tenant-1"),
                        new MaintenanceWorkflowInitializedEvent(
                        ID, tasks, NOW, "operator-1", "tenant-1"))
                .andGivenCommands(
                        new ClaimMaintenanceWorkflowTaskCommand(
                                ID, DATA_TASK_ID, "interleaved-claim-first", "operator-1"),
                        new ClaimMaintenanceWorkflowTaskCommand(
                                ID, secondDataTaskId, "interleaved-claim-second", "operator-2"),
                        new StartMaintenanceWorkflowTaskCommand(
                                ID, DATA_TASK_ID, "interleaved-start-first", "operator-1"),
                        new StartMaintenanceWorkflowTaskCommand(
                                ID, secondDataTaskId, "interleaved-start-second", "operator-2"),
                        new CompleteMaintenanceWorkflowTaskCommand(
                                ID, DATA_TASK_ID, "interleaved-complete-first",
                                null, null, "DATA_RECORDED", "第一保全项录入完成", "operator-1"))
                .when(new CompleteMaintenanceWorkflowTaskCommand(
                        ID, secondDataTaskId, "interleaved-complete-second",
                        null, null, "DATA_RECORDED", "第二保全项录入完成", "operator-2"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    List<MaintenanceWorkflowTask> current = aggregate.getWorkflowTasks();
                    assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, current.get(0).status());
                    assertEquals(MaintenanceWorkflowTaskStatus.READY, current.get(1).status());
                    assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, current.get(2).status());
                    assertEquals(MaintenanceWorkflowTaskStatus.READY, current.get(3).status());
                });
    }

    @Test
    void shouldKeepEffectPendingWhileCollectionIsPending() {
        MaintenanceBillingPostingEvidence posting = posting(MaintenanceBillingPostingStatus.POSTED);
        MaintenanceFundSettlementEvidence funds = funds(MaintenanceFundSettlementStatus.PENDING);

        fixture.given(quotedFeeWorkflowEvents())
                .when(new RecordMaintenancePremiumSettlementCommand(
                        ID, FEE_TASK_ID, "operation-settlement-pending", posting, funds,
                        "settlement-service"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL,
                            aggregate.getWorkflowTasks().getFirst().status());
                    assertEquals(MaintenanceWorkflowTaskStatus.PENDING,
                            aggregate.getWorkflowTasks().get(1).status());
                });
    }

    @Test
    void shouldKeepEffectPendingWhenFundSettlementFails() {
        MaintenanceBillingPostingEvidence posting = posting(MaintenanceBillingPostingStatus.POSTED);
        MaintenanceFundSettlementEvidence funds = funds(MaintenanceFundSettlementStatus.FAILED);

        fixture.given(quotedFeeWorkflowEvents())
                .when(new RecordMaintenancePremiumSettlementCommand(
                        ID, FEE_TASK_ID, "operation-settlement-failed", posting, funds,
                        "settlement-service"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(MaintenanceWorkflowTaskStatus.FAILED,
                            aggregate.getWorkflowTasks().getFirst().status());
                    assertEquals(MaintenanceWorkflowTaskStatus.PENDING,
                            aggregate.getWorkflowTasks().get(1).status());
                });
    }

    @Test
    void shouldKeepEffectPendingWhenBillingPostingIsReversed() {
        MaintenanceBillingPostingEvidence posting = posting(MaintenanceBillingPostingStatus.REVERSED);
        MaintenanceFundSettlementEvidence funds = funds(MaintenanceFundSettlementStatus.SUCCEEDED);

        fixture.given(quotedFeeWorkflowEvents())
                .when(new RecordMaintenancePremiumSettlementCommand(
                        ID, FEE_TASK_ID, "operation-settlement-reversed", posting, funds,
                        "settlement-service"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(MaintenanceWorkflowTaskStatus.FAILED,
                            aggregate.getWorkflowTasks().getFirst().status());
                    assertEquals(MaintenanceWorkflowTaskStatus.PENDING,
                            aggregate.getWorkflowTasks().get(1).status());
                });
    }

    @Test
    void shouldEnterEffectingWithoutCompletingEffectTask() {
        MaintenanceEffectRequestEvidence request = effectRequest();

        fixture.given(createdEvent(), initializedEvent(), effectWorkflowInitializedEvent())
                .when(new RequestMaintenanceEffectCommand(
                        ID, EFFECT_TASK_ID, "effect-request-operation", request, "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceWorkflowTaskTransitionedEvent.class),
                        instanceOf(MaintenanceEffectStatusChangedEvent.class))))
                .expectState(aggregate -> {
                    MaintenanceWorkflowTask task = aggregate.getWorkflowTasks().getFirst();
                    assertEquals(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL, task.status());
                    assertEquals(request, task.effectEvidence().request());
                    assertEquals(MaintenanceEffectStatus.EFFECTING, aggregate.getEffectStatus());
                    assertEquals(MaintenanceStatus.PENDING, aggregate.getStatus());
                });
    }

    @Test
    void shouldCompleteOnlyAfterMatchingPolicyApplicationReceipt() {
        MaintenanceEffectRequestEvidence request = effectRequest();
        MaintenanceWorkflowTask ready = readyEffectTask();
        MaintenanceWorkflowOperation requestOperation = effectRequestOperation(request);
        MaintenanceWorkflowTask waiting = ready.requestEffect(request, requestOperation);
        MaintenancePolicyApplicationEvidence receipt = policyApplication(request);

        fixture.given(createdEvent(), initializedEvent(), effectWorkflowInitializedEvent(),
                        transition(ready, waiting, null, null, requestOperation),
                        effectStatusChanged(MaintenanceEffectStatus.NOT_STARTED,
                                MaintenanceEffectStatus.EFFECTING))
                .when(new RecordMaintenancePolicyApplicationCommand(
                        ID, EFFECT_TASK_ID, "effect-receipt-operation", receipt, "policy-service"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceWorkflowTaskTransitionedEvent.class),
                        instanceOf(MaintenanceEffectStatusChangedEvent.class))))
                .expectState(aggregate -> {
                    MaintenanceWorkflowTask task = aggregate.getWorkflowTasks().getFirst();
                    assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, task.status());
                    assertEquals(receipt, task.effectEvidence().application());
                    assertEquals(MaintenanceEffectStatus.APPLIED, aggregate.getEffectStatus());
                    assertEquals(MaintenanceStatus.COMPLETED, aggregate.getStatus());
                });
    }

    @Test
    void shouldIgnoreScheduleFailureAfterPolicyHasApplied() {
        MaintenanceEffectRequestEvidence request = scheduledEffectRequest();

        fixture.given(futureCreatedEvent(), initializedEvent(), effectWorkflowInitializedEvent())
                .andGivenCommands(
                        scheduleCommand(),
                        attemptCommand(),
                        new RequestMaintenanceCaseEffectCommand(
                                ID, List.of(EFFECT_TASK_ID), "scheduled-effect-request", request, "operator-1"),
                        new RecordMaintenanceCasePolicyApplicationCommand(
                                ID, List.of(EFFECT_TASK_ID), "scheduled-effect-receipt",
                                policyApplication(request), "policy-service"))
                .when(new RecordMaintenanceEffectScheduleFailureCommand(
                        ID, "workflow-case-1:effect", "attempt-1", "CLOSE_FAILED",
                        "计划关闭失败", null, true, "scheduler"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents()
                .expectState(aggregate -> {
                    assertEquals(MaintenanceEffectStatus.APPLIED, aggregate.getEffectStatus());
                    assertEquals(MaintenanceEffectScheduleStatus.ACTIVE,
                            aggregate.getEffectSchedule().status());
                });
    }

    @Test
    void shouldTreatRepeatedScheduleCompletionAsIdempotent() {
        MaintenanceEffectRequestEvidence request = scheduledEffectRequest();
        CompleteMaintenanceEffectScheduleCommand complete = new CompleteMaintenanceEffectScheduleCommand(
                ID, "workflow-case-1:effect", "attempt-1", NOW.plusMinutes(4), "scheduler");

        fixture.given(futureCreatedEvent(), initializedEvent(), effectWorkflowInitializedEvent())
                .andGivenCommands(
                        scheduleCommand(),
                        attemptCommand(),
                        new RequestMaintenanceCaseEffectCommand(
                                ID, List.of(EFFECT_TASK_ID), "scheduled-effect-request", request, "operator-1"),
                        new RecordMaintenanceCasePolicyApplicationCommand(
                                ID, List.of(EFFECT_TASK_ID), "scheduled-effect-receipt",
                                policyApplication(request), "policy-service"),
                        complete)
                .when(complete)
                .expectSuccessfulHandlerExecution()
                .expectNoEvents()
                .expectState(aggregate -> assertEquals(
                        MaintenanceEffectScheduleStatus.COMPLETED, aggregate.getEffectSchedule().status()));
    }

    @Test
    void shouldTreatRepeatedSchedulePauseAndResumeAsIdempotent() {
        PauseMaintenanceEffectScheduleCommand pause = new PauseMaintenanceEffectScheduleCommand(
                ID, "workflow-case-1:effect", "等待人工确认", "operator-1");
        ResumeMaintenanceEffectScheduleCommand resume = new ResumeMaintenanceEffectScheduleCommand(
                ID, "workflow-case-1:effect", "resume-operation-1",
                NOW.plusDays(2), "确认后恢复", "operator-1");

        fixture.given(futureCreatedEvent(), initializedEvent(), effectWorkflowInitializedEvent())
                .andGivenCommands(scheduleCommand(), pause)
                .when(pause)
                .expectSuccessfulHandlerExecution()
                .expectNoEvents()
                .expectState(aggregate -> assertEquals(
                        MaintenanceEffectScheduleStatus.PAUSED, aggregate.getEffectSchedule().status()));

        fixture.given(futureCreatedEvent(), initializedEvent(), effectWorkflowInitializedEvent())
                .andGivenCommands(scheduleCommand(), pause, resume)
                .when(resume)
                .expectSuccessfulHandlerExecution()
                .expectNoEvents()
                .expectState(aggregate -> assertEquals(
                        MaintenanceEffectScheduleStatus.ACTIVE, aggregate.getEffectSchedule().status()));
    }

    @Test
    void shouldRejectPolicyReceiptForDifferentRequest() {
        MaintenanceEffectRequestEvidence request = effectRequest();
        MaintenanceWorkflowTask ready = readyEffectTask();
        MaintenanceWorkflowOperation requestOperation = effectRequestOperation(request);
        MaintenanceWorkflowTask waiting = ready.requestEffect(request, requestOperation);
        MaintenancePolicyApplicationEvidence mismatched = new MaintenancePolicyApplicationEvidence(
                "different-request", "ED-1", 7, 8, "c".repeat(64),
                appliedSnapshot(), List.of(appliedField()), NOW.plusMinutes(2));

        fixture.given(createdEvent(), initializedEvent(), effectWorkflowInitializedEvent(),
                        transition(ready, waiting, null, null, requestOperation),
                        effectStatusChanged(MaintenanceEffectStatus.NOT_STARTED,
                                MaintenanceEffectStatus.EFFECTING))
                .when(new RecordMaintenancePolicyApplicationCommand(
                        ID, EFFECT_TASK_ID, "mismatched-receipt-operation", mismatched, "policy-service"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRecordEffectFailureWithoutLosingRequestEvidence() {
        MaintenanceEffectRequestEvidence request = effectRequest();
        MaintenanceWorkflowTask ready = readyEffectTask();
        MaintenanceWorkflowOperation requestOperation = effectRequestOperation(request);
        MaintenanceWorkflowTask waiting = ready.requestEffect(request, requestOperation);

        fixture.given(createdEvent(), initializedEvent(), effectWorkflowInitializedEvent(),
                        transition(ready, waiting, null, null, requestOperation),
                        effectStatusChanged(MaintenanceEffectStatus.NOT_STARTED,
                                MaintenanceEffectStatus.EFFECTING))
                .when(new FailMaintenanceEffectCommand(
                        ID, EFFECT_TASK_ID, "effect-failure-operation",
                        "POLICY_UNAVAILABLE", "Policy 服务不可用", "effect-service"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    MaintenanceWorkflowTask task = aggregate.getWorkflowTasks().getFirst();
                    assertEquals(MaintenanceWorkflowTaskStatus.FAILED, task.status());
                    assertEquals(request, task.effectEvidence().request());
                    assertEquals(MaintenanceEffectStatus.FAILED, aggregate.getEffectStatus());
                });
    }

    @Test
    void shouldFreezeAndApplyAllCaseEffectTasksAtomically() {
        MaintenanceEffectRequestEvidence request = effectRequest();
        MaintenancePolicyApplicationEvidence receipt = policyApplication(request);
        List<String> taskIds = List.of(EFFECT_TASK_ID, SECOND_EFFECT_TASK_ID);

        fixture.given(createdEvent(), multiItemInitializedEvent(), multiEffectWorkflowInitializedEvent())
                .andGivenCommands(new RequestMaintenanceCaseEffectCommand(
                        ID, taskIds, "case-effect-request", request, "operator-1"))
                .when(new RecordMaintenanceCasePolicyApplicationCommand(
                        ID, taskIds, "case-effect-receipt", receipt, "policy-service"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceWorkflowTaskTransitionedEvent.class),
                        instanceOf(MaintenanceWorkflowTaskTransitionedEvent.class),
                        instanceOf(MaintenanceEffectStatusChangedEvent.class))))
                .expectState(aggregate -> {
                    assertEquals(MaintenanceEffectStatus.APPLIED, aggregate.getEffectStatus());
                    aggregate.getWorkflowTasks().forEach(task -> {
                        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, task.status());
                        assertEquals(receipt, task.effectEvidence().application());
                    });
                });
    }

    @Test
    void shouldRejectPartialCaseEffectRequestWithoutAnyEvent() {
        fixture.given(createdEvent(), multiItemInitializedEvent(), multiEffectWorkflowInitializedEvent())
                .when(new RequestMaintenanceCaseEffectCommand(
                        ID, List.of(EFFECT_TASK_ID), "case-effect-request", effectRequest(), "operator-1"))
                .expectException(MaintenanceValidationException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRecordAndResolveIndependentEffectCompensationFact() {
        MaintenanceEffectRequestEvidence request = effectRequest();
        MaintenancePolicyApplicationEvidence receipt = policyApplication(request);
        MaintenanceEffectCompensationEvidence compensation = new MaintenanceEffectCompensationEvidence(
                "compensation-1", request.requestId(), receipt.endorsementNo(),
                receipt.actualPolicyVersion(), receipt.applicationHash(),
                "案件回执写入失败", NOW.plusMinutes(3), "operator-1");

        fixture.given(createdEvent(), initializedEvent(), effectWorkflowInitializedEvent())
                .andGivenCommands(
                        new RequestMaintenanceCaseEffectCommand(
                                ID, List.of(EFFECT_TASK_ID), "case-effect-request", request, "operator-1"),
                        new RecordMaintenanceEffectCompensationCommand(
                                ID, EFFECT_TASK_ID, compensation, "operator-1"))
                .when(new RecordMaintenanceCasePolicyApplicationCommand(
                        ID, List.of(EFFECT_TASK_ID), "case-effect-receipt", receipt, "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceWorkflowTaskTransitionedEvent.class),
                        instanceOf(MaintenanceEffectStatusChangedEvent.class),
                        instanceOf(MaintenanceEffectCompensationResolvedEvent.class))))
                .expectState(aggregate -> {
                    assertEquals(false, aggregate.isEffectCompensationRequired());
                    assertEquals(compensation, aggregate.getEffectCompensationEvidence());
                    assertEquals(MaintenanceEffectStatus.APPLIED, aggregate.getEffectStatus());
                });
    }

    @Test
    void shouldExposeCompensationFactBeforeManualRetry() {
        MaintenanceEffectRequestEvidence request = effectRequest();
        MaintenancePolicyApplicationEvidence receipt = policyApplication(request);
        MaintenanceEffectCompensationEvidence compensation = new MaintenanceEffectCompensationEvidence(
                "compensation-1", request.requestId(), receipt.endorsementNo(),
                receipt.actualPolicyVersion(), receipt.applicationHash(),
                "案件回执写入失败", NOW.plusMinutes(3), "operator-1");

        fixture.given(createdEvent(), initializedEvent(), effectWorkflowInitializedEvent())
                .andGivenCommands(new RequestMaintenanceCaseEffectCommand(
                        ID, List.of(EFFECT_TASK_ID), "case-effect-request", request, "operator-1"))
                .when(new RecordMaintenanceEffectCompensationCommand(
                        ID, EFFECT_TASK_ID, compensation, "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceEffectCompensationRequiredEvent.class),
                        instanceOf(MaintenanceEffectStatusChangedEvent.class))))
                .expectState(aggregate -> {
                    assertEquals(true, aggregate.isEffectCompensationRequired());
                    assertEquals(compensation, aggregate.getEffectCompensationEvidence());
                    assertEquals(MaintenanceEffectStatus.FAILED, aggregate.getEffectStatus());
                });
    }

    @Test
    void shouldRejectCompensationWhenCaseEffectTasksAreNotAllWaitingForReceipt() {
        MaintenanceEffectRequestEvidence request = effectRequest();
        MaintenancePolicyApplicationEvidence receipt = policyApplication(request);
        MaintenanceEffectCompensationEvidence compensation = new MaintenanceEffectCompensationEvidence(
                "compensation-1", request.requestId(), receipt.endorsementNo(),
                receipt.actualPolicyVersion(), receipt.applicationHash(),
                "案件回执写入失败", NOW.plusMinutes(3), "operator-1");

        fixture.given(createdEvent(), multiItemInitializedEvent(), multiEffectWorkflowInitializedEvent())
                .when(new RecordMaintenanceEffectCompensationCommand(
                        ID, EFFECT_TASK_ID, compensation, "operator-1"))
                .expectException(MaintenanceValidationException.class)
                .expectNoEvents();
    }

    @Test
    void shouldRejectCompensationForDifferentPolicyRequest() {
        MaintenanceEffectRequestEvidence request = effectRequest();
        MaintenancePolicyApplicationEvidence receipt = policyApplication(request);
        MaintenanceEffectCompensationEvidence compensation = new MaintenanceEffectCompensationEvidence(
                "compensation-1", "different-request", receipt.endorsementNo(),
                receipt.actualPolicyVersion(), receipt.applicationHash(),
                "案件回执写入失败", NOW.plusMinutes(3), "operator-1");

        fixture.given(createdEvent(), initializedEvent(), effectWorkflowInitializedEvent())
                .andGivenCommands(new RequestMaintenanceCaseEffectCommand(
                        ID, List.of(EFFECT_TASK_ID), "case-effect-request", request, "operator-1"))
                .when(new RecordMaintenanceEffectCompensationCommand(
                        ID, EFFECT_TASK_ID, compensation, "operator-1"))
                .expectException(MaintenanceValidationException.class)
                .expectNoEvents();
    }

    @Test
    void shouldTreatRepeatedFundCallbackAsIdempotentWithoutDuplicateTransition() {
        MaintenanceWorkflowTask feeTask = feeTask();
        MaintenanceWorkflowTask quoted = quotedFeeTask();
        MaintenanceWorkflowTask effectTask = effectTask();
        MaintenanceBillingPostingEvidence posting = posting(MaintenanceBillingPostingStatus.POSTED);
        MaintenanceFundSettlementEvidence funds = funds(MaintenanceFundSettlementStatus.SUCCEEDED);
        MaintenanceWorkflowOperation quoteOperation = quoteOperation();
        MaintenanceWorkflowOperation settlementOperation = settlementOperation(
                "operation-settlement-idempotent", posting, funds);
        MaintenanceWorkflowTask settled = quoted.settlePremium(posting, funds, settlementOperation);
        MaintenanceWorkflowTask activatedEffect = effectTask.activate(settlementOperation);

        fixture.given(createdEvent(), initializedEvent(), new MaintenanceWorkflowInitializedEvent(
                                ID, List.of(feeTask, effectTask), NOW, "operator-1", "tenant-1"),
                        transition(feeTask, quoted, null, null, quoteOperation),
                        transition(quoted, settled, effectTask, activatedEffect, settlementOperation))
                .when(new RecordMaintenancePremiumSettlementCommand(
                        ID, FEE_TASK_ID, "operation-settlement-idempotent", posting, funds,
                        "settlement-service"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void shouldTreatProjectedSecondPrecisionSurrenderQuoteAsIdempotentReplay() {
        MaintenanceWorkflowTask feeTask = feeTask();
        MaintenancePremiumQuoteEvidence originalQuote = quoteEvidence(NOW.plusNanos(123456789));
        MaintenanceWorkflowOperation quoteOperation = operation(
                "operation-quote-replay", MaintenanceWorkflowAction.RECORD_PREMIUM_QUOTE,
                FEE_TASK_ID, originalQuote.evidenceVersion(), originalQuote.contentHash(),
                originalQuote.status().getCode(), originalQuote.detailSummary(), "pricing-service");
        MaintenanceWorkflowTask quoted = feeTask.recordPremiumQuote(originalQuote, quoteOperation);
        MaintenanceBillingPostingEvidence posting = posting(MaintenanceBillingPostingStatus.POSTED);
        MaintenanceFundSettlementEvidence funds = funds(MaintenanceFundSettlementStatus.SUCCEEDED);
        MaintenanceWorkflowOperation settlementOperation = settlementOperation(
                "operation-settlement-after-quote", posting, funds);
        MaintenanceWorkflowTask settled = quoted.settlePremium(posting, funds, settlementOperation);

        fixture.given(createdEvent(), initializedEvent(), new MaintenanceWorkflowInitializedEvent(
                                ID, List.of(feeTask), NOW, "operator-1", "tenant-1"),
                        transition(feeTask, quoted, null, null, quoteOperation),
                        transition(quoted, settled, null, null, settlementOperation))
                .when(new RecordMaintenancePremiumQuoteCommand(
                        ID, FEE_TASK_ID, "operation-quote-replay", quoteEvidence(NOW),
                        "pricing-service"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void shouldTreatSameOperationPayloadAsIdempotent() {
        MaintenanceWorkflowTask dataEntry = dataEntryTask();
        MaintenanceWorkflowOperation claim = operation(
                "operation-claim", MaintenanceWorkflowAction.CLAIM, DATA_TASK_ID,
                null, null, null, null, "operator-1");
        MaintenanceWorkflowTask claimed = dataEntry.claim(claim);

        fixture.given(createdEvent(), initializedEvent(), workflowInitializedEvent(),
                        transition(dataEntry, claimed, null, null, claim))
                .when(new ClaimMaintenanceWorkflowTaskCommand(
                        ID, DATA_TASK_ID, "operation-claim", "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void shouldRejectSameOperationIdWithDifferentPayload() {
        MaintenanceWorkflowTask dataEntry = dataEntryTask();
        MaintenanceWorkflowOperation claim = operation(
                "operation-claim", MaintenanceWorkflowAction.CLAIM, DATA_TASK_ID,
                null, null, null, null, "operator-1");
        MaintenanceWorkflowTask claimed = dataEntry.claim(claim);

        fixture.given(createdEvent(), initializedEvent(), workflowInitializedEvent(),
                        transition(dataEntry, claimed, null, null, claim))
                .when(new ClaimMaintenanceWorkflowTaskCommand(
                        ID, DATA_TASK_ID, "operation-claim", "operator-2"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectOutOfOrderTaskStart() {
        fixture.given(baseEvents())
                .when(new StartMaintenanceWorkflowTaskCommand(
                        ID, VALIDATION_TASK_ID, "operation-start", "operator-1"))
                .expectException(MaintenanceConflictException.class);
    }

    @Test
    void shouldRejectCreatorClaimingManualReviewTask() {
        MaintenanceWorkflowTask review = reviewTask();

        fixture.given(createdEvent(), initializedEvent(), new MaintenanceWorkflowInitializedEvent(
                        ID, List.of(review), NOW, "operator-1", "tenant-1"))
                .when(new ClaimMaintenanceWorkflowTaskCommand(
                        ID, REVIEW_TASK_ID, "operation-creator-review-claim", "operator-1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectCreatorDecidingHistoricallyAssignedManualReviewTask() {
        MaintenanceWorkflowTask review = reviewTask();
        MaintenanceWorkflowOperation claim = operation(
                "operation-legacy-claim", MaintenanceWorkflowAction.CLAIM, REVIEW_TASK_ID,
                null, null, null, null, "operator-1");
        MaintenanceWorkflowTask claimed = review.claim(claim);
        MaintenanceWorkflowOperation start = operation(
                "operation-legacy-start", MaintenanceWorkflowAction.START, REVIEW_TASK_ID,
                null, null, null, null, "operator-1");
        MaintenanceWorkflowTask started = claimed.start(start);
        MaintenanceWorkflowReviewEvidence evidence = new MaintenanceWorkflowReviewEvidence(
                MaintenanceReviewMode.MANUAL, MaintenanceReviewDecision.APPROVE,
                "APPROVAL_STANDARD", "policy-v1", List.of(), "审核通过", NOW, "operator-1");

        fixture.given(createdEvent(), initializedEvent(), new MaintenanceWorkflowInitializedEvent(
                                ID, List.of(review), NOW, "operator-1", "tenant-1"),
                        transition(review, claimed, null, null, claim),
                        transition(claimed, started, null, null, start))
                .when(new DecideMaintenanceReviewCommand(
                        ID, REVIEW_TASK_ID, "operation-creator-review", evidence, "operator-1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectCaseWhenManualReviewRejects() {
        MaintenanceWorkflowTask review = reviewTask();
        MaintenanceWorkflowOperation claim = operation(
                "operation-claim", MaintenanceWorkflowAction.CLAIM, REVIEW_TASK_ID,
                null, null, null, null, "reviewer-1");
        MaintenanceWorkflowTask claimed = review.claim(claim);
        MaintenanceWorkflowOperation start = operation(
                "operation-start", MaintenanceWorkflowAction.START, REVIEW_TASK_ID,
                null, null, null, null, "reviewer-1");
        MaintenanceWorkflowTask started = claimed.start(start);
        MaintenanceWorkflowReviewEvidence evidence = new MaintenanceWorkflowReviewEvidence(
                MaintenanceReviewMode.MANUAL, MaintenanceReviewDecision.REJECT,
                "APPROVAL_STANDARD", "policy-v1", List.of(), "身份材料不一致", NOW, "reviewer-1");

        fixture.given(createdEvent(), initializedEvent(), new MaintenanceWorkflowInitializedEvent(
                                ID, List.of(review), NOW, "operator-1", "tenant-1"),
                        transition(review, claimed, null, null, claim),
                        transition(claimed, started, null, null, start))
                .when(new DecideMaintenanceReviewCommand(
                        ID, REVIEW_TASK_ID, "operation-review", evidence, "reviewer-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceWorkflowTaskTransitionedEvent.class),
                        instanceOf(MaintenanceCaseRejectedByReviewEvent.class))))
                .expectState(aggregate -> {
                    assertEquals(MaintenanceStatus.REJECTED, aggregate.getStatus());
                    assertEquals(MaintenanceWorkflowTaskStatus.REJECTED,
                            aggregate.getWorkflowTasks().getFirst().status());
                });
    }

    @Test
    void shouldTreatRepeatedReviewCallbackAsIdempotentWithoutDuplicateRejection() {
        MaintenanceWorkflowTask review = reviewTask();
        MaintenanceWorkflowOperation claim = operation(
                "operation-claim", MaintenanceWorkflowAction.CLAIM, REVIEW_TASK_ID,
                null, null, null, null, "reviewer-1");
        MaintenanceWorkflowTask claimed = review.claim(claim);
        MaintenanceWorkflowOperation start = operation(
                "operation-start", MaintenanceWorkflowAction.START, REVIEW_TASK_ID,
                null, null, null, null, "reviewer-1");
        MaintenanceWorkflowTask started = claimed.start(start);
        MaintenanceWorkflowReviewEvidence evidence = new MaintenanceWorkflowReviewEvidence(
                MaintenanceReviewMode.MANUAL, MaintenanceReviewDecision.REJECT,
                "APPROVAL_STANDARD", "policy-v1", List.of(), "身份材料不一致", NOW, "reviewer-1");
        MaintenanceWorkflowOperation reviewOperation = operation(
                "operation-review", MaintenanceWorkflowAction.DECIDE_REVIEW, REVIEW_TASK_ID,
                evidence.policyVersion(), evidence.contentHash(), evidence.decision().getCode(),
                evidence.comment(), "reviewer-1");
        MaintenanceWorkflowTask rejected = started.decideReview(evidence, reviewOperation);

        fixture.given(createdEvent(), initializedEvent(), new MaintenanceWorkflowInitializedEvent(
                                ID, List.of(review), NOW, "operator-1", "tenant-1"),
                        transition(review, claimed, null, null, claim),
                        transition(claimed, started, null, null, start),
                        transition(started, rejected, null, null, reviewOperation),
                        new MaintenanceCaseRejectedByReviewEvent(
                                ID, REVIEW_TASK_ID, evidence.contentHash(), evidence.policyCode(),
                                evidence.policyVersion(), evidence.comment(), NOW, "reviewer-1", "tenant-1"))
                .when(new DecideMaintenanceReviewCommand(
                        ID, REVIEW_TASK_ID, "operation-review", evidence, "reviewer-1"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    private Object[] baseEvents() {
        return new Object[]{createdEvent(), initializedEvent(), workflowInitializedEvent()};
    }

    private MaintenanceCreatedEvent createdEvent() {
        return new MaintenanceCreatedEvent(
                ID, PolicyId.of("policy-1"), CustomerId.of("customer-1"),
                MaintenanceType.POLICY_INFO_CHANGE, EffectiveTimeType.IMMEDIATE,
                null, "测试案件", NOW, "operator-1", "tenant-1");
    }

    private MaintenanceCreatedEvent futureCreatedEvent() {
        return new MaintenanceCreatedEvent(
                ID, PolicyId.of("policy-1"), CustomerId.of("customer-1"),
                MaintenanceType.POLICY_INFO_CHANGE, EffectiveTimeType.FUTURE,
                NOW.plusDays(1), "未来生效测试案件", NOW, "operator-1", "tenant-1");
    }

    private MaintenanceCaseInitializationCompletedEvent initializedEvent() {
        return new MaintenanceCaseInitializationCompletedEvent(
                ID, List.of(ITEM_CODE), NOW, "operator-1", "tenant-1");
    }

    private MaintenanceItemAddedEvent itemAddedEvent(String itemCode, String fieldCode) {
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                itemCode, "1.0.0", itemCode, MaintenanceItemCategory.BASIC_INFORMATION,
                Set.of(MaintenanceChannel.MANUAL),
                List.of(MaintenanceFieldRule.editable(
                        fieldCode, false, false, PolicyFieldValueType.TEXT)),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), false);
        return new MaintenanceItemAddedEvent(
                ID, MaintenanceItemInstance.from(definition, NOW), NOW, "operator-1", "tenant-1");
    }

    private MaintenanceFieldChangesRecordedEvent fieldChangesRecordedEvent(
            String itemCode, String objectId, String fieldCode) {
        MaintenanceFieldChange change = MaintenanceFieldChange.propose(
                itemCode, objectId, fieldCode,
                MaintenanceFieldValue.text("original"), MaintenanceFieldValue.text("changed"));
        return new MaintenanceFieldChangesRecordedEvent(
                ID, itemCode, List.of(change), NOW, "operator-1", "tenant-1");
    }

    private MaintenanceWorkflowInitializedEvent workflowInitializedEvent() {
        return new MaintenanceWorkflowInitializedEvent(
                ID, List.of(dataEntryTask(), validationTask()), NOW, "operator-1", "tenant-1");
    }

    private MaintenanceWorkflowTask dataEntryTask() {
        return new MaintenanceWorkflowTask(
                DATA_TASK_ID, ITEM_CODE, 0, 1,
                MaintenanceStepType.DATA_ENTRY, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.READY);
    }

    private MaintenanceWorkflowTask validationTask() {
        return new MaintenanceWorkflowTask(
                VALIDATION_TASK_ID, ITEM_CODE, 0, 2,
                MaintenanceStepType.VALIDATION, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.PENDING);
    }

    private MaintenanceWorkflowTask reviewTask() {
        return new MaintenanceWorkflowTask(
                REVIEW_TASK_ID, ITEM_CODE, 0, 1,
                MaintenanceStepType.REVIEW, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.READY);
    }

    private MaintenanceWorkflowInitializedEvent phaseFourWorkflowInitializedEvent() {
        return new MaintenanceWorkflowInitializedEvent(
                ID,
                List.of(
                        dataEntryTask(),
                        new MaintenanceWorkflowTask(
                                REVIEW_TASK_ID, ITEM_CODE, 0, 2,
                                MaintenanceStepType.REVIEW, MaintenanceStepMode.CONDITIONAL,
                                "review-rule", MaintenanceWorkflowTaskStatus.PENDING),
                        new MaintenanceWorkflowTask(
                                UNDERWRITING_TASK_ID, ITEM_CODE, 0, 3,
                                MaintenanceStepType.UNDERWRITING, MaintenanceStepMode.REQUIRED,
                                null, MaintenanceWorkflowTaskStatus.PENDING),
                        new MaintenanceWorkflowTask(
                                FEE_TASK_ID, ITEM_CODE, 0, 4,
                                MaintenanceStepType.FEE_SETTLEMENT, MaintenanceStepMode.REQUIRED,
                                null, MaintenanceWorkflowTaskStatus.PENDING),
                        new MaintenanceWorkflowTask(
                                EFFECT_TASK_ID, ITEM_CODE, 0, 5,
                                MaintenanceStepType.EFFECT, MaintenanceStepMode.REQUIRED,
                                null, MaintenanceWorkflowTaskStatus.PENDING)),
                NOW, "operator-1", "tenant-1");
    }

    private MaintenanceWorkflowInitializedEvent effectWorkflowInitializedEvent() {
        return new MaintenanceWorkflowInitializedEvent(
                ID, List.of(readyEffectTask()), NOW, "operator-1", "tenant-1");
    }

    private MaintenanceCaseInitializationCompletedEvent multiItemInitializedEvent() {
        return new MaintenanceCaseInitializationCompletedEvent(
                ID, List.of(ITEM_CODE, "BENEFICIARY_CHANGE"), NOW, "operator-1", "tenant-1");
    }

    private MaintenanceWorkflowInitializedEvent multiEffectWorkflowInitializedEvent() {
        MaintenanceWorkflowTask second = new MaintenanceWorkflowTask(
                SECOND_EFFECT_TASK_ID, "BENEFICIARY_CHANGE", 1, 1,
                MaintenanceStepType.EFFECT, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.READY);
        return new MaintenanceWorkflowInitializedEvent(
                ID, List.of(readyEffectTask(), second), NOW, "operator-1", "tenant-1");
    }

    private MaintenanceWorkflowTask readyEffectTask() {
        return new MaintenanceWorkflowTask(
                EFFECT_TASK_ID, ITEM_CODE, 0, 1, MaintenanceStepType.EFFECT,
                MaintenanceStepMode.REQUIRED, null, MaintenanceWorkflowTaskStatus.READY);
    }

    private MaintenanceEffectRequestEvidence effectRequest() {
        return new MaintenanceEffectRequestEvidence(
                "effect-request-1", "a".repeat(64), 7, EffectiveTimeType.IMMEDIATE,
                NOW.plusMinutes(1), "b".repeat(64), NOW);
    }

    private MaintenanceEffectRequestEvidence scheduledEffectRequest() {
        return new MaintenanceEffectRequestEvidence(
                "scheduled-effect-request-1", "a".repeat(64), 7, EffectiveTimeType.FUTURE,
                NOW.plusDays(1), "b".repeat(64), NOW.plusMinutes(2));
    }

    private ScheduleMaintenanceEffectCommand scheduleCommand() {
        return new ScheduleMaintenanceEffectCommand(
                ID, "workflow-case-1:effect", "Asia/Shanghai", NOW.plusDays(1), "scheduler");
    }

    private RecordMaintenanceEffectScheduleAttemptCommand attemptCommand() {
        return new RecordMaintenanceEffectScheduleAttemptCommand(
                ID, "workflow-case-1:effect", "attempt-1", NOW.plusMinutes(1), "scheduler");
    }

    private MaintenancePolicyApplicationEvidence policyApplication(
            MaintenanceEffectRequestEvidence request) {
        return new MaintenancePolicyApplicationEvidence(
                request.requestId(), "ED-1", request.expectedPolicyVersion(), 8,
                "c".repeat(64), appliedSnapshot(), List.of(appliedField()), NOW.plusMinutes(2));
    }

    private MaintenanceSnapshotReference appliedSnapshot() {
        return new MaintenanceSnapshotReference(
                "axon-event://policy/policy-1/maintenance/workflow-case-1",
                "d".repeat(64), 8, OffsetDateTime.parse("2026-08-25T11:02:00+08:00"));
    }

    private MaintenanceAppliedFieldEvidence appliedField() {
        return new MaintenanceAppliedFieldEvidence(
                ITEM_CODE, "policy-1", "policy.holder.mobile",
                PolicyFieldDataType.TEXT, "13900000000");
    }

    private MaintenanceWorkflowOperation effectRequestOperation(
            MaintenanceEffectRequestEvidence request) {
        return operation(
                "effect-request-operation", MaintenanceWorkflowAction.REQUEST_EFFECT,
                EFFECT_TASK_ID, request.evidenceVersion(), request.requestPayloadHash(),
                MaintenanceEffectStatus.EFFECTING.getCode(), null, "operator-1");
    }

    private MaintenanceEffectStatusChangedEvent effectStatusChanged(
            MaintenanceEffectStatus previous,
            MaintenanceEffectStatus current) {
        return new MaintenanceEffectStatusChangedEvent(
                ID, EFFECT_TASK_ID, previous, current, "测试状态变化", NOW, "operator-1", "tenant-1");
    }

    private MaintenanceWorkflowReviewEvidence automaticReviewEvidence() {
        List<MaintenanceReviewGateEvidence> gates = Arrays.stream(MaintenanceReviewGate.values())
                .map(gate -> new MaintenanceReviewGateEvidence(
                        gate, true, "b".repeat(64), gate.getCode() + "_PASSED"))
                .toList();
        return new MaintenanceWorkflowReviewEvidence(
                MaintenanceReviewMode.AUTOMATIC, MaintenanceReviewDecision.APPROVE,
                "APPROVAL_STANDARD", "policy-v1", gates,
                "七类门禁全部通过", NOW.plusMinutes(1), "review-engine");
    }

    private MaintenanceUnderwritingEvidence approvedUnderwritingEvidence() {
        return new MaintenanceUnderwritingEvidence(
                "underwriting-case-1", "c".repeat(64), "rule-v1", "model-v1",
                MaintenanceUnderwritingConclusion.APPROVED, List.of(),
                "核保通过", NOW.plusMinutes(2));
    }

    private Object[] quotedFeeWorkflowEvents() {
        MaintenanceWorkflowTask feeTask = feeTask();
        MaintenanceWorkflowTask quoted = quotedFeeTask();
        return new Object[]{
                createdEvent(), initializedEvent(),
                new MaintenanceWorkflowInitializedEvent(
                        ID, List.of(feeTask, effectTask()), NOW, "operator-1", "tenant-1"),
                transition(feeTask, quoted, null, null, quoteOperation())};
    }

    private MaintenanceWorkflowTask feeTask() {
        return new MaintenanceWorkflowTask(
                FEE_TASK_ID, ITEM_CODE, 0, 1, MaintenanceStepType.FEE_SETTLEMENT,
                MaintenanceStepMode.REQUIRED, null, MaintenanceWorkflowTaskStatus.READY);
    }

    private MaintenanceWorkflowTask effectTask() {
        return new MaintenanceWorkflowTask(
                EFFECT_TASK_ID, ITEM_CODE, 0, 2, MaintenanceStepType.EFFECT,
                MaintenanceStepMode.REQUIRED, null, MaintenanceWorkflowTaskStatus.PENDING);
    }

    private MaintenanceWorkflowTask quotedFeeTask() {
        return feeTask().recordPremiumQuote(quoteEvidence(), quoteOperation());
    }

    private MaintenancePremiumQuoteEvidence quoteEvidence() {
        return quoteEvidence(NOW);
    }

    private MaintenancePremiumQuoteEvidence quoteEvidence(LocalDateTime quotedAt) {
        return new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.QUOTED, "quote-1", "a".repeat(64), "b".repeat(64),
                "original", "c".repeat(64), "replacement", "d".repeat(64),
                "plan-v2", "e".repeat(64), "f".repeat(64), "DEBIT 20 CNY; lines=1",
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "CNY",
                quotedAt, quotedAt.plusHours(24));
    }

    private MaintenanceWorkflowOperation quoteOperation() {
        MaintenancePremiumQuoteEvidence quote = quoteEvidence();
        return operation(
                "operation-quote-before-settlement", MaintenanceWorkflowAction.RECORD_PREMIUM_QUOTE,
                FEE_TASK_ID, quote.evidenceVersion(), quote.contentHash(), quote.status().getCode(),
                quote.detailSummary(), "pricing-service");
    }

    private MaintenanceBillingPostingEvidence posting(MaintenanceBillingPostingStatus status) {
        return new MaintenanceBillingPostingEvidence(
                "posting-1", "quote-1", "f".repeat(64), MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20"), "CNY", status, 0, NOW.plusMinutes(1));
    }

    private MaintenanceFundSettlementEvidence funds(MaintenanceFundSettlementStatus status) {
        return new MaintenanceFundSettlementEvidence(
                MaintenanceFundSettlementType.COLLECTION, status, "posting-1", null, "payment-1",
                status == MaintenanceFundSettlementStatus.SUCCEEDED ? "SUCCESS" : status.getCode(),
                new BigDecimal("20"), "CNY",
                status.failed() ? "PAYMENT_FAILED" : null,
                status.failed() ? "渠道确认收款失败" : null,
                NOW.plusMinutes(2));
    }

    private MaintenanceWorkflowOperation settlementOperation(
            String operationId,
            MaintenanceBillingPostingEvidence posting,
            MaintenanceFundSettlementEvidence funds) {
        String resultCode = posting.status() == MaintenanceBillingPostingStatus.REVERSED
                ? posting.status().getCode()
                : funds.status().getCode();
        return operation(
                operationId, MaintenanceWorkflowAction.SETTLE_PREMIUM, FEE_TASK_ID,
                funds.evidenceVersion(posting), funds.gateContentHash(posting), resultCode,
                funds.detailSummary(), "settlement-service");
    }

    private MaintenanceWorkflowOperation operation(
            String operationId,
            MaintenanceWorkflowAction action,
            String taskId,
            String evidenceVersion,
            String evidenceHash,
            String resultCode,
            String reason,
            String operatorId) {
        return MaintenanceWorkflowOperation.create(
                operationId, action, taskId, evidenceVersion, evidenceHash,
                resultCode, reason, NOW, operatorId);
    }

    private MaintenanceWorkflowTaskTransitionedEvent transition(
            MaintenanceWorkflowTask before,
            MaintenanceWorkflowTask after,
            MaintenanceWorkflowTask activatedBefore,
            MaintenanceWorkflowTask activatedAfter,
            MaintenanceWorkflowOperation operation) {
        return new MaintenanceWorkflowTaskTransitionedEvent(
                ID, before, after, activatedBefore, activatedAfter,
                operation.operationId(), operation.payloadHash(), NOW,
                operation.operatedBy(), "tenant-1");
    }
}
