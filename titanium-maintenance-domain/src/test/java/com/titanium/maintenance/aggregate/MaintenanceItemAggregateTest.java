package com.titanium.maintenance.aggregate;

import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.axonframework.test.matchers.Matchers.payloadsMatching;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.command.AddMaintenanceItemCommand;
import com.titanium.maintenance.command.CompleteMaintenanceCaseInitializationCommand;
import com.titanium.maintenance.command.ConfigureMaintenanceItemWithdrawalRecoveryCommand;
import com.titanium.maintenance.command.InitializeMaintenanceWorkflowCommand;
import com.titanium.maintenance.command.ProposeMaintenanceFieldChangesCommand;
import com.titanium.maintenance.command.RecordMaintenanceFieldChangesCommand;
import com.titanium.maintenance.command.RecordMaintenanceItemWithdrawalCompensationCommand;
import com.titanium.maintenance.command.RefreshMaintenanceFieldConflictsCommand;
import com.titanium.maintenance.command.ResolveMaintenanceFieldConflictCommand;
import com.titanium.maintenance.command.StartMaintenanceItemWithdrawalCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalFundAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.event.MaintenanceCaseInitializationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceCaseItemsPlannedEvent;
import com.titanium.maintenance.event.MaintenanceCaseOpenedEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceEffectStatusChangedEvent;
import com.titanium.maintenance.event.MaintenanceFieldChangesRecordedEvent;
import com.titanium.maintenance.event.MaintenanceFieldConflictResolvedEvent;
import com.titanium.maintenance.event.MaintenanceFieldConflictsRefreshedEvent;
import com.titanium.maintenance.event.MaintenanceItemAddedEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalCompensationRecordedEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalRecoveryConfiguredEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalStartedEvent;
import com.titanium.maintenance.event.MaintenancePolicySnapshotCapturedEvent;
import com.titanium.maintenance.event.MaintenanceProposedSnapshotRecordedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowInitializedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowTaskTransitionedEvent;
import com.titanium.maintenance.service.MaintenanceFieldConflictPlanner;
import com.titanium.maintenance.service.MaintenanceWorkflowPlanner;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldCatalogSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldDescriptorSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldProposal;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.maintenance.valueobject.item.MaintenanceItemSelectionEvidence;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawal;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawalCompensation;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawalRecoveryContext;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

class MaintenanceItemAggregateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 10, 0);
    private static final MaintenanceId ID = MaintenanceId.of("maintenance-items-1");

    private FixtureConfiguration<Maintenance> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Maintenance.class);
    }

    @Test
    void shouldAddCompatibleItemsAndFreezeVersions() {
        fixture.given(createdEvent())
                .when(new AddMaintenanceItemCommand(ID, definition("CONTACT_CHANGE", Set.of()), "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> {
                    assertEquals(1, aggregate.getItemInstances().size());
                    assertEquals("1.0.0", aggregate.getItemInstances().getFirst().configVersion());
                });
    }

    @Test
    void shouldTreatSameFrozenItemAsIdempotent() {
        MaintenanceItemInstance item = MaintenanceItemInstance.from(
                definition("CONTACT_CHANGE", Set.of()), NOW);

        fixture.given(createdEvent(), new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1"))
                .when(new AddMaintenanceItemCommand(ID, definition("CONTACT_CHANGE", Set.of()), "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void shouldRejectDuplicateItemWithDifferentVersion() {
        MaintenanceItemInstance item = MaintenanceItemInstance.from(
                definition("CONTACT_CHANGE", "1.0.0", Set.of(), false), NOW);

        fixture.given(createdEvent(), new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1"))
                .when(new AddMaintenanceItemCommand(
                        ID, definition("CONTACT_CHANGE", "2.0.0", Set.of(), false), "operator-1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectIncompatibleItem() {
        MaintenanceItemInstance surrender = MaintenanceItemInstance.from(
                definition("SURRENDER", Set.of("CONTACT_CHANGE")), NOW);

        fixture.given(createdEvent(), new MaintenanceItemAddedEvent(ID, surrender, NOW, "operator-1", "1"))
                .when(new AddMaintenanceItemCommand(ID, definition("CONTACT_CHANGE", Set.of()), "operator-1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectCombinationWhenExistingItemIsAtomicOnly() {
        MaintenanceItemInstance atomicItem = MaintenanceItemInstance.from(
                definition("POLICY_TERMINATION", "1.0.0", Set.of(), true), NOW);

        fixture.given(createdEvent(), new MaintenanceItemAddedEvent(ID, atomicItem, NOW, "operator-1", "1"))
                .when(new AddMaintenanceItemCommand(
                        ID, definition("CONTACT_CHANGE", "1.0.0", Set.of(), false), "operator-1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldFreezeAuthoritativeEvidenceAndCompleteInitialization() {
        MaintenanceItemDefinition definition = definition(
                "POLICY_INFO_CHANGE", "1.0.0", Set.of(), false);
        MaintenanceItemSelectionEvidence evidence = evidence("configuration-1", "1.0.0");
        MaintenanceItemInstance item = MaintenanceItemInstance.from(definition, evidence, NOW);

        fixture.given(createdEvent(), openedEvent(),
                        new MaintenanceCaseItemsPlannedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"),
                        new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1"))
                .when(new CompleteMaintenanceCaseInitializationCommand(
                        ID, List.of("POLICY_INFO_CHANGE"), "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceCaseInitializationCompletedEvent.class),
                        instanceOf(MaintenanceWorkflowInitializedEvent.class))))
                .expectState(aggregate -> {
                    assertTrue(aggregate.isInitializationCompleted());
                    assertEquals(evidence, aggregate.getItemInstances().getFirst().selectionEvidence());
                    assertEquals(definition.controls(), aggregate.getItemInstances().getFirst().controls());
                    assertEquals(3, aggregate.getWorkflowTasks().size());
                });
    }

    @Test
    void shouldBackfillWorkflowForCaseInitializedBeforeWorkflowEventWasIntroduced() {
        MaintenanceItemDefinition definition = definition(
                "POLICY_INFO_CHANGE", "1.0.0", Set.of(), false);
        MaintenanceItemInstance item = MaintenanceItemInstance.from(
                definition, evidence("configuration-1", "1.0.0"), NOW);

        fixture.given(createdEvent(), openedEvent(),
                        new MaintenanceCaseItemsPlannedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"),
                        new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1"),
                        new MaintenanceCaseInitializationCompletedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"))
                .when(new InitializeMaintenanceWorkflowCommand(ID, "operator-2"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceWorkflowInitializedEvent.class))))
                .expectState(aggregate -> assertEquals(3, aggregate.getWorkflowTasks().size()));
    }

    @Test
    void shouldRejectWithdrawalForOnlyActiveItem() {
        MaintenanceItemInstance item = proposalItem(
                "POLICY_INFO_CHANGE",
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)));

        fixture.given(withdrawalReadyEvents(List.of(item)))
                .when(withdrawalCommand("POLICY_INFO_CHANGE"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldFreezeMultiItemWithdrawalAndTreatSameRequestAsIdempotent() {
        List<MaintenanceItemInstance> items = withdrawalItems();
        StartMaintenanceItemWithdrawalCommand command = withdrawalCommand("POLICY_INFO_CHANGE");
        MaintenanceItemWithdrawal withdrawal = MaintenanceItemWithdrawal.requested(
                "POLICY_INFO_CHANGE", "withdraw-operation-1", "9".repeat(64), "客户取消该变更项",
                null, null, NOW.plusHours(1), "operator-1");

        fixture.given(concat(
                        withdrawalReadyEvents(items),
                        new MaintenanceItemWithdrawalStartedEvent(ID, withdrawal, "1")))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void shouldFreezeWithdrawalRecoveryContextBeforeExternalCompensation() {
        MaintenanceItemWithdrawal withdrawal = MaintenanceItemWithdrawal.requested(
                "POLICY_INFO_CHANGE", "withdraw-operation-1", "9".repeat(64), "客户取消该变更项",
                null, null, NOW.plusHours(1), "operator-1");

        fixture.given(concat(
                        withdrawalReadyEvents(withdrawalItems()),
                        new MaintenanceItemWithdrawalStartedEvent(ID, withdrawal, "1")))
                .when(recoveryCommand())
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceItemWithdrawalRecoveryConfiguredEvent.class))))
                .expectState(aggregate -> assertEquals("BANK_CARD",
                        aggregate.getItemWithdrawalRecoveryContexts()
                                .get("POLICY_INFO_CHANGE").paymentMethod()));
    }

    @Test
    void shouldTreatSameWithdrawalRecoveryContextAsIdempotent() {
        MaintenanceItemWithdrawal withdrawal = MaintenanceItemWithdrawal.requested(
                "POLICY_INFO_CHANGE", "withdraw-operation-1", "9".repeat(64), "客户取消该变更项",
                null, null, NOW.plusHours(1), "operator-1");
        MaintenanceItemWithdrawalRecoveryContext context = new MaintenanceItemWithdrawalRecoveryContext(
                "POLICY_INFO_CHANGE", "withdraw-operation-1", "9".repeat(64),
                "BANK_CARD", NOW.plusHours(1), "operator-1");

        fixture.given(concat(
                        withdrawalReadyEvents(withdrawalItems()),
                        new MaintenanceItemWithdrawalStartedEvent(ID, withdrawal, "1"),
                        new MaintenanceItemWithdrawalRecoveryConfiguredEvent(ID, context, "1")))
                .when(recoveryCommand())
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void shouldCompleteWithdrawalAndSkipOnlyUnfinishedItemTasks() {
        List<MaintenanceItemInstance> items = withdrawalItems();
        MaintenanceItemWithdrawal withdrawal = MaintenanceItemWithdrawal.requested(
                "POLICY_INFO_CHANGE", "withdraw-operation-1", "9".repeat(64), "客户取消该变更项",
                null, null, NOW.plusHours(1), "operator-1");
        MaintenanceItemWithdrawalCompensation compensation = new MaintenanceItemWithdrawalCompensation(
                null, null, MaintenanceItemWithdrawalFundAction.NOT_REQUIRED,
                MaintenanceFundSettlementStatus.NOT_REQUIRED, null, null, "NOT_REQUIRED",
                BigDecimal.ZERO, null, null, null, NOW.plusHours(2));

        fixture.given(concat(
                        withdrawalReadyEvents(items),
                        new MaintenanceItemWithdrawalStartedEvent(ID, withdrawal, "1")))
                .when(new RecordMaintenanceItemWithdrawalCompensationCommand(
                        ID, "POLICY_INFO_CHANGE", "withdraw-operation-1", "9".repeat(64),
                        compensation, "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceItemWithdrawalCompensationRecordedEvent.class),
                        instanceOf(MaintenanceWorkflowTaskTransitionedEvent.class),
                        instanceOf(MaintenanceWorkflowTaskTransitionedEvent.class))))
                .expectState(aggregate -> {
                    assertEquals(MaintenanceItemWithdrawalStatus.COMPLETED,
                            aggregate.getItemWithdrawals().get("POLICY_INFO_CHANGE").status());
                    assertTrue(aggregate.getWorkflowTasks().stream()
                            .filter(task -> task.itemCode().equals("POLICY_INFO_CHANGE"))
                            .allMatch(task -> task.status() == MaintenanceWorkflowTaskStatus.SKIPPED));
                });
    }

    @Test
    void shouldTreatExistingWorkflowInitializationAsIdempotent() {
        MaintenanceItemDefinition definition = definition(
                "POLICY_INFO_CHANGE", "1.0.0", Set.of(), false);
        MaintenanceItemInstance item = MaintenanceItemInstance.from(
                definition, evidence("configuration-1", "1.0.0"), NOW);
        var tasks = new MaintenanceWorkflowPlanner().plan(ID, List.of(item));

        fixture.given(createdEvent(), openedEvent(),
                        new MaintenanceCaseItemsPlannedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"),
                        new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1"),
                        new MaintenanceCaseInitializationCompletedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"),
                        new MaintenanceWorkflowInitializedEvent(ID, tasks, NOW, "operator-1", "1"))
                .when(new InitializeMaintenanceWorkflowCommand(ID, "operator-2"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void shouldRecordConfiguredFieldChanges() {
        MaintenanceItemInstance item = MaintenanceItemInstance.from(
                definition("CONTACT_CHANGE", Set.of()), NOW);
        MaintenanceFieldChange change = contactChange("policy.contact.mobile");

        fixture.given(createdEvent(), new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1"))
                .when(new RecordMaintenanceFieldChangesCommand(
                        ID, "CONTACT_CHANGE", List.of(change), "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> assertEquals(
                        List.of(change), aggregate.getItemInstances().getFirst().fieldChanges()));
    }

    @Test
    void shouldRejectFieldOutsideConfiguredWhitelist() {
        MaintenanceItemInstance item = MaintenanceItemInstance.from(
                definition("CONTACT_CHANGE", Set.of()), NOW);
        MaintenanceFieldChange change = contactChange("policy.coverage.sumInsured");

        fixture.given(createdEvent(), new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1"))
                .when(new RecordMaintenanceFieldChangesCommand(
                        ID, "CONTACT_CHANGE", List.of(change), "operator-1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldGenerateStructuredChangesAndProposedSnapshot() {
        MaintenanceItemDefinition definition = proposalDefinition();
        MaintenanceItemInstance item = MaintenanceItemInstance.from(
                definition, evidence("configuration-1", "1.0.0"), NOW);
        PolicyMaintenanceSnapshot baseSnapshot = policySnapshot(7, "13800000000");
        PolicyMaintenanceSnapshot currentSnapshot = policySnapshot(8, "13800000000");

        fixture.given(createdEvent(), openedEvent(),
                        new MaintenancePolicySnapshotCapturedEvent(
                                ID, baseSnapshot, NOW, "operator-1", "1"),
                        new MaintenanceCaseItemsPlannedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"),
                        new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1"),
                        new MaintenanceCaseInitializationCompletedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"))
                .when(new ProposeMaintenanceFieldChangesCommand(
                        ID, "POLICY_INFO_CHANGE", currentSnapshot,
                        List.of(new MaintenanceFieldProposal(
                                null, "policy.holder.mobile", PolicyFieldDataType.TEXT, "13900000000")),
                        fieldCatalog(), "operator-1", "1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceFieldChangesRecordedEvent.class),
                        instanceOf(MaintenanceProposedSnapshotRecordedEvent.class))))
                .expectState(aggregate -> {
                    MaintenanceFieldChange change = aggregate.getItemInstances().getFirst()
                            .fieldChanges().getFirst();
                    assertEquals("13800000000", change.baseValue().canonicalValue());
                    assertEquals("13800000000", change.currentValue().canonicalValue());
                    assertEquals("13900000000", change.proposedValue().canonicalValue());
                    assertEquals("13900000000", aggregate.getProposedFieldValues()
                            .get("policy.holder.mobile").canonicalValue());
                    assertEquals(8, aggregate.getSnapshotSet().proposedSnapshot().policyVersion());
                    assertEquals(PolicyFieldMaskingPolicy.MOBILE, aggregate.getFieldCatalogSnapshots()
                            .get("POLICY_INFO_CHANGE").requireField("policy.holder.mobile").maskingPolicy());
                });
    }

    @Test
    void shouldRejectProposalTypeDifferentFromFieldCatalog() {
        MaintenanceItemDefinition definition = proposalDefinition();
        MaintenanceItemInstance item = MaintenanceItemInstance.from(
                definition, evidence("configuration-1", "1.0.0"), NOW);
        PolicyMaintenanceSnapshot snapshot = policySnapshot(7, "13800000000");

        fixture.given(createdEvent(), openedEvent(),
                        new MaintenancePolicySnapshotCapturedEvent(
                                ID, snapshot, NOW, "operator-1", "1"),
                        new MaintenanceCaseItemsPlannedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"),
                        new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1"),
                        new MaintenanceCaseInitializationCompletedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"))
                .when(new ProposeMaintenanceFieldChangesCommand(
                        ID, "POLICY_INFO_CHANGE", snapshot,
                        List.of(new MaintenanceFieldProposal(
                                null, "policy.holder.mobile", PolicyFieldDataType.INTEGER, "13900000000")),
                        fieldCatalog(), "operator-1", "1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRequireStableObjectIdForCollectionField() {
        String fieldCode = "policy.insured.mobile";
        MaintenanceItemInstance item = proposalItem(
                "INSURED_CHANGE",
                List.of(MaintenanceFieldRule.editable(fieldCode, true, true, PolicyFieldValueType.TEXT)));
        PolicyMaintenanceSnapshot snapshot = policySnapshot(7, "13800000000");

        fixture.given(initializedEvents(List.of(item), snapshot))
                .when(new ProposeMaintenanceFieldChangesCommand(
                        ID, "INSURED_CHANGE", snapshot,
                        List.of(new MaintenanceFieldProposal(null, fieldCode, PolicyFieldDataType.TEXT, "13900000000")),
                        collectionFieldCatalog(fieldCode), "operator-1", "1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectSameBusinessFieldChangedByMultipleItems() {
        String fieldCode = "policy.holder.mobile";
        MaintenanceItemInstance contactItem = proposalItem(
                "CONTACT_CHANGE",
                List.of(MaintenanceFieldRule.editable(fieldCode, true, true, PolicyFieldValueType.TEXT)));
        MaintenanceItemInstance correctionItem = proposalItem(
                "POLICY_INFO_CHANGE",
                List.of(MaintenanceFieldRule.editable(fieldCode, true, true, PolicyFieldValueType.TEXT)));
        PolicyMaintenanceSnapshot snapshot = policySnapshot(7, "13800000000");
        MaintenanceFieldChange existingChange = MaintenanceFieldChange.propose(
                "CONTACT_CHANGE", "policy-1", fieldCode,
                MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"));

        fixture.given(createdEvent(), openedEvent(),
                        new MaintenancePolicySnapshotCapturedEvent(ID, snapshot, NOW, "operator-1", "1"),
                        new MaintenanceCaseItemsPlannedEvent(
                                ID, List.of("CONTACT_CHANGE", "POLICY_INFO_CHANGE"), NOW, "operator-1", "1"),
                        new MaintenanceItemAddedEvent(ID, contactItem, NOW, "operator-1", "1"),
                        new MaintenanceFieldChangesRecordedEvent(
                                ID, "CONTACT_CHANGE", List.of(existingChange), NOW, "operator-1", "1"),
                        new MaintenanceItemAddedEvent(ID, correctionItem, NOW, "operator-1", "1"),
                        new MaintenanceCaseInitializationCompletedEvent(
                                ID, List.of("CONTACT_CHANGE", "POLICY_INFO_CHANGE"), NOW, "operator-1", "1"))
                .when(new ProposeMaintenanceFieldChangesCommand(
                        ID, "POLICY_INFO_CHANGE", snapshot,
                        List.of(new MaintenanceFieldProposal(
                                null, fieldCode, PolicyFieldDataType.TEXT, "13700000000")),
                        fieldCatalog(), "operator-1", "1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldDetectCurrentPolicyValueDriftFromCaseBaseline() {
        MaintenanceItemInstance item = proposalItem(
                "POLICY_INFO_CHANGE",
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)));
        PolicyMaintenanceSnapshot baseSnapshot = policySnapshot(7, "13800000000");
        PolicyMaintenanceSnapshot currentSnapshot = policySnapshot(8, "13700000000");

        fixture.given(initializedEvents(List.of(item), baseSnapshot))
                .when(new ProposeMaintenanceFieldChangesCommand(
                        ID, "POLICY_INFO_CHANGE", currentSnapshot,
                        List.of(new MaintenanceFieldProposal(
                                null, "policy.holder.mobile", PolicyFieldDataType.TEXT, "13900000000")),
                        fieldCatalog(), "operator-1", "1"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> assertEquals(
                        MaintenanceFieldConflictStatus.DETECTED,
                        aggregate.getItemInstances().getFirst().fieldChanges().getFirst().conflictStatus()));
    }

    @Test
    void shouldRefreshConflictAndChangeEffectStatus() {
        PolicyMaintenanceSnapshot latest = policySnapshot(8, "13700000000");

        fixture.given(conflictReadyEvents())
                .when(new RefreshMaintenanceFieldConflictsCommand(
                        ID, "refresh-1", "a".repeat(64), latest,
                        OffsetDateTime.parse("2026-08-26T08:00:00Z"), "operator-2", "1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceFieldConflictsRefreshedEvent.class),
                        instanceOf(MaintenanceEffectStatusChangedEvent.class))))
                .expectState(aggregate -> {
                    assertEquals(MaintenanceEffectStatus.CONFLICTED, aggregate.getEffectStatus());
                    assertEquals(8, aggregate.getSnapshotSet().proposedSnapshot().policyVersion());
                    assertTrue(aggregate.getItemInstances().getFirst().fieldChanges().getFirst()
                            .hasUnresolvedConflict());
                });
    }

    @Test
    void shouldResolveLastConflictAndRestoreEffectStatus() {
        OffsetDateTime refreshedAt = OffsetDateTime.parse("2026-08-26T08:00:00Z");
        MaintenanceFieldConflictPlan conflictPlan = conflictPlan(refreshedAt);

        fixture.given(concat(conflictReadyEvents(),
                        new MaintenanceFieldConflictsRefreshedEvent(
                                ID, "refresh-1", "a".repeat(64), conflictPlan,
                                refreshedAt, "operator-2", "1"),
                        new MaintenanceEffectStatusChangedEvent(
                                ID, "maintenance-items-1:POLICY_INFO_CHANGE:EFFECT:3",
                                MaintenanceEffectStatus.NOT_STARTED, MaintenanceEffectStatus.CONFLICTED,
                                "检测到顺序外字段冲突", NOW, "operator-2", "1")))
                .when(new ResolveMaintenanceFieldConflictCommand(
                        ID, "resolve-1", "b".repeat(64), "POLICY_INFO_CHANGE", "policy-1",
                        "policy.holder.mobile", MaintenanceFieldConflictResolutionAction.USE_CURRENT,
                        null, "采用 Policy 当前联系方式", refreshedAt.plusMinutes(1), "operator-2", "1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceFieldConflictResolvedEvent.class),
                        instanceOf(MaintenanceEffectStatusChangedEvent.class))))
                .expectState(aggregate -> {
                    assertEquals(MaintenanceEffectStatus.NOT_STARTED, aggregate.getEffectStatus());
                    MaintenanceFieldChange change = aggregate.getItemInstances().getFirst()
                            .fieldChanges().getFirst();
                    assertEquals(change.currentValue(), change.proposedValue());
                    assertEquals(MaintenanceFieldConflictStatus.RESOLVED, change.conflictStatus());
                });
    }

    @Test
    void shouldRejectReusedConflictOperationWithDifferentPayload() {
        OffsetDateTime refreshedAt = OffsetDateTime.parse("2026-08-26T08:00:00Z");
        MaintenanceFieldConflictPlan plan = conflictPlan(refreshedAt);

        fixture.given(concat(conflictReadyEvents(), new MaintenanceFieldConflictsRefreshedEvent(
                        ID, "refresh-1", "a".repeat(64), plan, refreshedAt, "operator-2", "1")))
                .when(new RefreshMaintenanceFieldConflictsCommand(
                        ID, "refresh-1", "b".repeat(64), policySnapshot(8, "13700000000"),
                        refreshedAt, "operator-2", "1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldTreatSameAuthoritativeFieldDraftAsIdempotent() {
        MaintenanceItemInstance item = proposalItem(
                "POLICY_INFO_CHANGE",
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)));
        PolicyMaintenanceSnapshot snapshot = policySnapshot(7, "13800000000");
        MaintenanceFieldChange change = MaintenanceFieldChange.propose(
                "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"));
        Map<String, MaintenanceFieldValue> proposedValues = Map.of(
                "policy.holder.mobile", MaintenanceFieldValue.text("13900000000"));
        MaintenanceSnapshotReference proposedReference = new MaintenanceSnapshotReference(
                "axon-event://maintenance/1/maintenance-items-1/proposed?hash=" + "e".repeat(64),
                "e".repeat(64), 7, OffsetDateTime.parse("2026-08-24T08:02:00Z"));

        fixture.given(createdEvent(), openedEvent(),
                        new MaintenancePolicySnapshotCapturedEvent(ID, snapshot, NOW, "operator-1", "1"),
                        new MaintenanceCaseItemsPlannedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"),
                        new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1"),
                        new MaintenanceCaseInitializationCompletedEvent(
                                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "1"),
                        new MaintenanceFieldChangesRecordedEvent(
                                ID, "POLICY_INFO_CHANGE", List.of(change), NOW, "operator-1", "1"),
                        new MaintenanceProposedSnapshotRecordedEvent(
                                ID, "POLICY_INFO_CHANGE", proposedReference, proposedValues,
                                fieldCatalog(), OffsetDateTime.parse("2026-08-24T08:02:00Z"),
                                "operator-1", "1"))
                .when(new ProposeMaintenanceFieldChangesCommand(
                        ID, "POLICY_INFO_CHANGE", snapshot,
                        List.of(new MaintenanceFieldProposal(
                                null, "policy.holder.mobile", PolicyFieldDataType.TEXT, "13900000000")),
                        fieldCatalog(), "operator-1", "1"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void shouldRejectLegacyFieldCommandForIndependentCase() {
        MaintenanceItemInstance item = proposalItem(
                "POLICY_INFO_CHANGE",
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)));
        PolicyMaintenanceSnapshot snapshot = policySnapshot(7, "13800000000");
        MaintenanceFieldChange change = MaintenanceFieldChange.propose(
                "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"));

        fixture.given(initializedEvents(List.of(item), snapshot))
                .when(new RecordMaintenanceFieldChangesCommand(
                        ID, "POLICY_INFO_CHANGE", List.of(change), "operator-1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectClearWhenConfigurationDisallowsIt() {
        MaintenanceItemInstance item = proposalItem(
                "POLICY_INFO_CHANGE",
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, false, PolicyFieldValueType.TEXT)));
        PolicyMaintenanceSnapshot snapshot = policySnapshot(7, "13800000000");

        fixture.given(initializedEvents(List.of(item), snapshot))
                .when(new ProposeMaintenanceFieldChangesCommand(
                        ID, "POLICY_INFO_CHANGE", snapshot,
                        List.of(new MaintenanceFieldProposal(
                                null, "policy.holder.mobile", PolicyFieldDataType.TEXT, null)),
                        fieldCatalog(), "operator-1", "1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectDraftMissingUnconditionalRequiredField() {
        MaintenanceItemInstance item = proposalItem(
                "POLICY_INFO_CHANGE",
                List.of(
                        MaintenanceFieldRule.editable(
                                "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT),
                        MaintenanceFieldRule.editable(
                                "policy.holder.name", true, false, PolicyFieldValueType.TEXT)));
        PolicyMaintenanceSnapshot snapshot = policySnapshot(7, "13800000000");

        fixture.given(initializedEvents(List.of(item), snapshot))
                .when(new ProposeMaintenanceFieldChangesCommand(
                        ID, "POLICY_INFO_CHANGE", snapshot,
                        List.of(new MaintenanceFieldProposal(
                                null, "policy.holder.mobile", PolicyFieldDataType.TEXT, "13900000000")),
                        fieldCatalog(), "operator-1", "1"))
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectProposalCountAndValueLengthBeyondLimits() {
        assertThrows(MaintenanceValidationException.class, () -> new MaintenanceFieldProposal(
                null, "policy.holder.mobile", PolicyFieldDataType.TEXT, "x".repeat(32769)));

        MaintenanceItemInstance item = proposalItem(
                "POLICY_INFO_CHANGE",
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)));
        PolicyMaintenanceSnapshot snapshot = policySnapshot(7, "13800000000");
        List<MaintenanceFieldProposal> proposals = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> new MaintenanceFieldProposal(
                        null, "policy.holder.mobile", PolicyFieldDataType.TEXT, "13900000000"))
                .toList();

        fixture.given(initializedEvents(List.of(item), snapshot))
                .when(new ProposeMaintenanceFieldChangesCommand(
                        ID, "POLICY_INFO_CHANGE", snapshot, proposals,
                        fieldCatalog(), "operator-1", "1"))
                .expectException(MaintenanceValidationException.class);
    }

    private MaintenanceCreatedEvent createdEvent() {
        return new MaintenanceCreatedEvent(ID, PolicyId.of("policy-1"), CustomerId.of("customer-1"),
                MaintenanceType.POLICY_INFO_CHANGE, EffectiveTimeType.IMMEDIATE, null,
                "联系方式变更", NOW, "operator-1", "1");
    }

    private MaintenanceCaseOpenedEvent openedEvent() {
        return new MaintenanceCaseOpenedEvent(
                ID, MaintenanceChannel.MANUAL, "request-1", "fingerprint-1", NOW, "operator-1", "1");
    }

    private MaintenanceItemDefinition definition(String itemCode, Set<String> incompatibleItemCodes) {
        return definition(itemCode, "1.0.0", incompatibleItemCodes, false);
    }

    private MaintenanceItemDefinition definition(
            String itemCode, String version, Set<String> incompatibleItemCodes, boolean atomicOnly) {
        return new MaintenanceItemDefinition(itemCode, version, itemCode,
                MaintenanceItemCategory.BASIC_INFORMATION, Set.of(MaintenanceChannel.MANUAL),
                List.of(MaintenanceFieldRule.editable("policy.contact.mobile", true, false)),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), incompatibleItemCodes, atomicOnly);
    }

    private MaintenanceItemSelectionEvidence evidence(String configurationId, String version) {
        return MaintenanceItemSelectionEvidence.authoritative(
                configurationId, version, "a".repeat(64),
                "offering-1", "offering-v1", "b".repeat(64),
                OffsetDateTime.parse("2026-08-24T10:00:00+08:00"));
    }

    private MaintenanceItemDefinition proposalDefinition() {
        return proposalDefinition(
                "POLICY_INFO_CHANGE",
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)));
    }

    private MaintenanceItemDefinition proposalDefinition(
            String itemCode, List<MaintenanceFieldRule> fieldRules) {
        return new MaintenanceItemDefinition(
                itemCode, "1.0.0", itemCode,
                MaintenanceItemCategory.BASIC_INFORMATION, Set.of(MaintenanceChannel.MANUAL),
                fieldRules,
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), false);
    }

    private MaintenanceItemInstance proposalItem(
            String itemCode, List<MaintenanceFieldRule> fieldRules) {
        return MaintenanceItemInstance.from(
                proposalDefinition(itemCode, fieldRules), evidence("configuration-" + itemCode, "1.0.0"), NOW);
    }

    private Object[] initializedEvents(
            List<MaintenanceItemInstance> items, PolicyMaintenanceSnapshot snapshot) {
        List<String> itemCodes = items.stream().map(MaintenanceItemInstance::itemCode).toList();
        List<Object> events = new java.util.ArrayList<>();
        events.add(createdEvent());
        events.add(openedEvent());
        events.add(new MaintenancePolicySnapshotCapturedEvent(ID, snapshot, NOW, "operator-1", "1"));
        events.add(new MaintenanceCaseItemsPlannedEvent(ID, itemCodes, NOW, "operator-1", "1"));
        items.forEach(item -> events.add(new MaintenanceItemAddedEvent(ID, item, NOW, "operator-1", "1")));
        events.add(new MaintenanceCaseInitializationCompletedEvent(ID, itemCodes, NOW, "operator-1", "1"));
        return events.toArray();
    }

    private Object[] conflictReadyEvents() {
        MaintenanceItemInstance item = proposalItem(
                "POLICY_INFO_CHANGE",
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)));
        PolicyMaintenanceSnapshot snapshot = policySnapshot(7, "13800000000");
        MaintenanceFieldChange change = MaintenanceFieldChange.propose(
                "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"));
        List<Object> events = new java.util.ArrayList<>(List.of(initializedEvents(List.of(item), snapshot)));
        events.add(new MaintenanceWorkflowInitializedEvent(
                ID, new MaintenanceWorkflowPlanner().plan(ID, List.of(item)), NOW, "operator-1", "1"));
        events.add(new MaintenanceFieldChangesRecordedEvent(
                ID, "POLICY_INFO_CHANGE", List.of(change), NOW, "operator-1", "1"));
        events.add(new MaintenanceProposedSnapshotRecordedEvent(
                ID, "POLICY_INFO_CHANGE", proposedSnapshot(7),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13900000000")),
                fieldCatalog(), OffsetDateTime.parse("2026-08-24T08:02:00Z"), "operator-1", "1"));
        return events.toArray();
    }

    private List<MaintenanceItemInstance> withdrawalItems() {
        return List.of(
                proposalItem(
                        "POLICY_INFO_CHANGE",
                        List.of(MaintenanceFieldRule.editable(
                                "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT))),
                proposalItem(
                        "ADDRESS_CHANGE",
                        List.of(MaintenanceFieldRule.editable(
                                "policy.holder.address", true, true, PolicyFieldValueType.TEXT))));
    }

    private Object[] withdrawalReadyEvents(List<MaintenanceItemInstance> items) {
        Object[] initialized = initializedEvents(items, policySnapshot(7, "13800000000"));
        return concat(initialized, new MaintenanceWorkflowInitializedEvent(
                ID, new MaintenanceWorkflowPlanner().plan(ID, items), NOW, "operator-1", "1"));
    }

    private StartMaintenanceItemWithdrawalCommand withdrawalCommand(String itemCode) {
        return new StartMaintenanceItemWithdrawalCommand(
                ID, itemCode, "withdraw-operation-1", "9".repeat(64),
                "客户取消该变更项", "operator-1", "1");
    }

    private ConfigureMaintenanceItemWithdrawalRecoveryCommand recoveryCommand() {
        return new ConfigureMaintenanceItemWithdrawalRecoveryCommand(
                ID, "POLICY_INFO_CHANGE", "withdraw-operation-1", "9".repeat(64),
                "BANK_CARD", "operator-1", "1");
    }

    private MaintenanceFieldConflictPlan conflictPlan(OffsetDateTime refreshedAt) {
        MaintenanceItemInstance item = proposalItem(
                "POLICY_INFO_CHANGE",
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)))
                .withFieldChanges(List.of(MaintenanceFieldChange.propose(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        MaintenanceFieldValue.text("13800000000"),
                        MaintenanceFieldValue.text("13900000000"))));
        return new MaintenanceFieldConflictPlanner().refresh(
                ID, "1", policySnapshot(8, "13700000000"), List.of(item), refreshedAt);
    }

    private MaintenanceSnapshotReference proposedSnapshot(long version) {
        return new MaintenanceSnapshotReference(
                "axon-event://maintenance/1/maintenance-items-1/proposed", "e".repeat(64), version,
                OffsetDateTime.parse("2026-08-24T08:02:00Z"));
    }

    private Object[] concat(Object[] existing, Object... additions) {
        List<Object> events = new java.util.ArrayList<>(List.of(existing));
        events.addAll(List.of(additions));
        return events.toArray();
    }

    private PolicyMaintenanceSnapshot policySnapshot(long version, String mobile) {
        return new PolicyMaintenanceSnapshot(
                "1", PolicyId.of("policy-1"), "P202608240001", CustomerId.of("customer-1"),
                "product-1", "product-v1", "plan-v1", PolicyStatus.EFFECTIVE, version,
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
                new MaintenanceSnapshotReference(
                        "axon-event://policy/1/policy-1?version=" + version,
                        "c".repeat(64), version, OffsetDateTime.parse("2026-08-24T08:00:00Z")),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text(mobile)));
    }

    private MaintenanceFieldCatalogSnapshot fieldCatalog() {
        MaintenanceFieldDescriptorSnapshot mobile = new MaintenanceFieldDescriptorSnapshot(
                "policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                "policy.field.holder.mobile", false, null, true, true, true, false,
                "POLICY_INFO_CHANGE", PolicyFieldSensitivityLevel.SENSITIVE,
                PolicyFieldMaskingPolicy.MOBILE, null);
        return new MaintenanceFieldCatalogSnapshot(
                "1", LocalDate.of(2026, 8, 1), "catalog-v1", "d".repeat(64),
                OffsetDateTime.parse("2026-08-24T08:01:00Z"), Map.of(mobile.fieldCode(), mobile));
    }

    private MaintenanceFieldCatalogSnapshot collectionFieldCatalog(String fieldCode) {
        MaintenanceFieldDescriptorSnapshot descriptor = new MaintenanceFieldDescriptorSnapshot(
                fieldCode, PolicyFieldObjectType.INSURED, PolicyFieldValueType.TEXT,
                "policy.field.insured.mobile", true, "insuredId", true, true, true, true,
                "INSURED_CHANGE", PolicyFieldSensitivityLevel.SENSITIVE,
                PolicyFieldMaskingPolicy.MOBILE, null);
        return new MaintenanceFieldCatalogSnapshot(
                "1", LocalDate.of(2026, 8, 1), "catalog-v1", "d".repeat(64),
                OffsetDateTime.parse("2026-08-24T08:01:00Z"), Map.of(fieldCode, descriptor));
    }

    private MaintenanceFieldChange contactChange(String fieldCode) {
        return MaintenanceFieldChange.propose("CONTACT_CHANGE", "policy-1", fieldCode,
                MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"));
    }
}
