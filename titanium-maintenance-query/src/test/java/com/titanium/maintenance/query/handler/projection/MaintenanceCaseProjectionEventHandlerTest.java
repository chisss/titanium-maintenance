package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.event.MaintenanceCaseInitializationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceCaseItemsPlannedEvent;
import com.titanium.maintenance.event.MaintenanceCaseOpenedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleAttemptedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduleFailedEvent;
import com.titanium.maintenance.event.MaintenanceEffectScheduledEvent;
import com.titanium.maintenance.event.MaintenanceEffectStatusChangedEvent;
import com.titanium.maintenance.event.MaintenanceFieldChangesRecordedEvent;
import com.titanium.maintenance.event.MaintenanceItemAddedEvent;
import com.titanium.maintenance.event.MaintenanceItemWithdrawalRecoveryConfiguredEvent;
import com.titanium.maintenance.event.MaintenancePolicySnapshotCapturedEvent;
import com.titanium.maintenance.event.MaintenanceProposedSnapshotRecordedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowTaskTransitionedEvent;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldCatalogSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldDescriptorSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.maintenance.valueobject.item.MaintenanceItemSelectionEvidence;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawalRecoveryContext;
import com.titanium.maintenance.valueobject.workflow.MaintenanceAppliedFieldEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectSchedule;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyApplicationEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenancePremiumQuoteEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowOperation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

class MaintenanceCaseProjectionEventHandlerTest {

    private static final MaintenanceId ID = MaintenanceId.of("case-1");
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-08-24T10:00:00");

    private MaintenanceViewRepository maintenanceViewRepository;
    private MaintenanceCaseItemViewRepository itemViewRepository;
    private MaintenanceFieldChangeViewRepository fieldViewRepository;
    private MaintenanceSnapshotViewRepository snapshotViewRepository;
    private MaintenanceCaseProjectionEventHandler handler;

    @BeforeEach
    void setUp() {
        maintenanceViewRepository = mock(MaintenanceViewRepository.class);
        itemViewRepository = mock(MaintenanceCaseItemViewRepository.class);
        fieldViewRepository = mock(MaintenanceFieldChangeViewRepository.class);
        snapshotViewRepository = mock(MaintenanceSnapshotViewRepository.class);
        handler = new MaintenanceCaseProjectionEventHandler(
                maintenanceViewRepository, itemViewRepository, fieldViewRepository, snapshotViewRepository);
    }

    @Test
    void shouldExposeIndependentCaseOnlyAfterInitializationCompletes() {
        MaintenanceView view = new MaintenanceView();
        when(maintenanceViewRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(view));

        handler.on(new MaintenanceCaseOpenedEvent(
                ID, MaintenanceChannel.MANUAL, "request-1", "a".repeat(64),
                NOW, "operator-1", "tenant-1"));

        assertTrue(view.isIndependentCase());
        assertFalse(view.isInitializationCompleted());
        assertEquals("request-1", view.getClientRequestKey());

        handler.on(new MaintenanceCaseItemsPlannedEvent(
                ID, List.of("POLICY_INFO_CHANGE"), NOW.plusMinutes(1), "operator-1", "tenant-1"));
        handler.on(new MaintenanceCaseInitializationCompletedEvent(
                ID, List.of("POLICY_INFO_CHANGE"), NOW.plusMinutes(2), "operator-1", "tenant-1"));

        assertEquals(1, view.getPlannedItemCount());
        assertTrue(view.isInitializationCompleted());
        assertEquals(NOW.plusMinutes(2), view.getInitializationCompletedAt());
    }

    @Test
    void shouldProjectFutureScheduleAttemptsAndRetryFailure() {
        MaintenanceView view = new MaintenanceView();
        when(maintenanceViewRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        LocalDateTime nextExecutionAt = NOW.plusDays(1);
        MaintenanceEffectSchedule schedule = MaintenanceEffectSchedule.create(
                "case-1:effect", EffectiveTimeType.FUTURE, "Asia/Shanghai", nextExecutionAt, NOW);

        handler.on(new MaintenanceEffectScheduledEvent(ID, schedule, "operator-1", "tenant-1"));
        handler.on(new MaintenanceEffectScheduleAttemptedEvent(
                ID, schedule.scheduleId(), "attempt-1", 1, NOW.plusMinutes(1),
                "scheduler", "tenant-1"));
        handler.on(new MaintenanceEffectScheduleFailedEvent(
                ID, schedule.scheduleId(), "attempt-1", "POLICY_UNAVAILABLE", "Policy 暂时不可用",
                NOW.plusMinutes(6), false, NOW.plusMinutes(2), "scheduler", "tenant-1"));

        assertEquals(MaintenanceEffectScheduleStatus.ACTIVE, view.getEffectScheduleStatus());
        assertEquals(1, view.getEffectScheduleAttemptCount());
        assertEquals("attempt-1", view.getEffectScheduleLastAttemptId());
        assertEquals("POLICY_UNAVAILABLE", view.getEffectScheduleLastErrorCode());
        assertEquals(NOW.plusMinutes(6), view.getEffectScheduleNextExecutionAt());
    }

    @Test
    void shouldProjectPolicySnapshotAndFrozenItemEvidence() {
        MaintenanceView mainView = new MaintenanceView();
        when(maintenanceViewRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(mainView));
        when(snapshotViewRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.empty());
        when(itemViewRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                "tenant-1", "case-1", "POLICY_INFO_CHANGE"))
                .thenReturn(Optional.empty());

        handler.on(new MaintenancePolicySnapshotCapturedEvent(
                ID, policySnapshot(), NOW, "operator-1", "tenant-1"));
        handler.on(new MaintenanceItemAddedEvent(
                ID, item(), NOW.plusMinutes(1), "operator-1", "tenant-1"));

        assertEquals("P202608240001", mainView.getPolicyNumber());
        assertEquals(7L, mainView.getPolicyBaselineVersion());
        ArgumentCaptor<MaintenanceSnapshotView> snapshotCaptor =
                ArgumentCaptor.forClass(MaintenanceSnapshotView.class);
        verify(snapshotViewRepository).save(snapshotCaptor.capture());
        assertEquals("b".repeat(64), snapshotCaptor.getValue().getBeforeContentHash());
        ArgumentCaptor<MaintenanceCaseItemView> itemCaptor =
                ArgumentCaptor.forClass(MaintenanceCaseItemView.class);
        verify(itemViewRepository).save(itemCaptor.capture());
        assertEquals("configuration-1", itemCaptor.getValue().getConfigurationId());
        assertEquals("offering-v1", itemCaptor.getValue().getOfferingVersion());
    }

    @Test
    void shouldProjectWithdrawalRecoveryPaymentMethodForRebuild() {
        MaintenanceCaseItemView itemView = new MaintenanceCaseItemView();
        when(itemViewRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                "tenant-1", "case-1", "POLICY_INFO_CHANGE"))
                .thenReturn(Optional.of(itemView));
        MaintenanceItemWithdrawalRecoveryContext context = new MaintenanceItemWithdrawalRecoveryContext(
                "POLICY_INFO_CHANGE", "withdraw-operation-1", "9".repeat(64),
                "BANK_CARD", NOW, "operator-1");

        handler.on(new MaintenanceItemWithdrawalRecoveryConfiguredEvent(ID, context, "tenant-1"));

        assertEquals("BANK_CARD", itemView.getWithdrawalPaymentMethod());
        assertEquals(NOW, itemView.getWithdrawalRecoveryConfiguredAt());
        assertEquals("operator-1", itemView.getWithdrawalUpdatedBy());
        assertEquals(NOW, itemView.getUpdateTime());
        verify(itemViewRepository).save(itemView);
    }

    @Test
    void shouldProjectUniquePremiumCalculationCheckpointAndFlagConflictingQuote() {
        MaintenanceView view = new MaintenanceView();
        when(maintenanceViewRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(view));

        handler.on(quotedTransition(
                "task-fee-1", "COVERAGE_AMOUNT_CHANGE", "quote-1",
                "original-calculation", "replacement-calculation", NOW));

        assertEquals("original-calculation", view.getOriginalCalculationId());
        assertEquals("replacement-calculation", view.getReplacementCalculationId());
        assertFalse(view.isPremiumCalculationCheckpointConflict());

        handler.on(quotedTransition(
                "task-fee-2", "PAYMENT_METHOD_CHANGE", "quote-2",
                "other-original", "other-replacement", NOW.plusMinutes(1)));

        assertTrue(view.isPremiumCalculationCheckpointConflict());
        assertEquals("original-calculation", view.getOriginalCalculationId());
        assertEquals("replacement-calculation", view.getReplacementCalculationId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldProjectFourValuesAndEnrichSensitiveCatalogEvidence() {
        MaintenanceFieldChange change = MaintenanceFieldChange.propose(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        MaintenanceFieldValue.text("13800000000"),
                        MaintenanceFieldValue.text("13900000000"))
                .refreshCurrent(MaintenanceFieldValue.text("13700000000"));
        handler.on(new MaintenanceFieldChangesRecordedEvent(
                ID, "POLICY_INFO_CHANGE", List.of(change), NOW, "operator-1", "tenant-1"));

        ArgumentCaptor<List<MaintenanceFieldChangeView>> fieldCaptor = ArgumentCaptor.forClass(List.class);
        verify(fieldViewRepository).saveAll(fieldCaptor.capture());
        MaintenanceFieldChangeView fieldView = fieldCaptor.getValue().getFirst();
        assertEquals("13800000000", fieldView.getBaseValue());
        assertEquals("13700000000", fieldView.getCurrentValue());
        assertEquals("13900000000", fieldView.getProposedValue());
        when(fieldViewRepository.findByTenantIdAndMaintenanceIdAndItemCodeOrderByFieldCodeAscObjectIdAsc(
                "tenant-1", "case-1", "POLICY_INFO_CHANGE"))
                .thenReturn(List.of(fieldView));
        when(snapshotViewRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(new MaintenanceSnapshotView()));

        MaintenanceSnapshotReference proposed = new MaintenanceSnapshotReference(
                "axon-event://maintenance/tenant-1/case-1/proposed", "c".repeat(64), 8,
                OffsetDateTime.parse("2026-08-24T10:01:00+08:00"));
        handler.on(new MaintenanceProposedSnapshotRecordedEvent(
                ID, "POLICY_INFO_CHANGE", proposed,
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13900000000")),
                fieldCatalog(), OffsetDateTime.parse("2026-08-24T10:01:00+08:00"),
                "operator-1", "tenant-1"));

        assertEquals(PolicyFieldSensitivityLevel.SENSITIVE, fieldView.getSensitivity());
        assertEquals(PolicyFieldMaskingPolicy.MOBILE, fieldView.getMaskingPolicy());
        assertEquals("policy.field.holder.mobile", fieldView.getLabelKey());
    }

    @Test
    void shouldProjectEffectStatusAppliedValueAndSnapshot() {
        MaintenanceView mainView = new MaintenanceView();
        when(maintenanceViewRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(mainView));
        handler.on(new MaintenanceEffectStatusChangedEvent(
                ID, "task-effect", MaintenanceEffectStatus.NOT_STARTED,
                MaintenanceEffectStatus.EFFECTING, "Policy 应用请求已冻结",
                NOW, "operator-1", "tenant-1"));
        assertEquals(MaintenanceEffectStatus.EFFECTING, mainView.getEffectStatus());

        handler.on(new MaintenanceEffectStatusChangedEvent(
                ID, "task-effect", MaintenanceEffectStatus.EFFECTING,
                MaintenanceEffectStatus.APPLIED, "Policy 权威回执已记录",
                NOW.plusMinutes(1), "operator-1", "tenant-1"));
        assertEquals(MaintenanceStatus.COMPLETED, mainView.getStatus());

        MaintenanceFieldChangeView fieldView = new MaintenanceFieldChangeView();
        fieldView.setObjectId("policy-1");
        fieldView.setFieldCode("policy.holder.mobile");
        when(fieldViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCodeOrderByFieldCodeAscObjectIdAsc(
                        "tenant-1", "case-1", "POLICY_INFO_CHANGE"))
                .thenReturn(List.of(fieldView));
        when(snapshotViewRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(new MaintenanceSnapshotView()));
        MaintenanceWorkflowTask ready = new MaintenanceWorkflowTask(
                "task-effect", "POLICY_INFO_CHANGE", 0, 3,
                MaintenanceStepType.EFFECT, MaintenanceStepMode.REQUIRED,
                null, MaintenanceWorkflowTaskStatus.READY);
        MaintenanceEffectRequestEvidence request = new MaintenanceEffectRequestEvidence(
                "effect-request-1", "a".repeat(64), 7, EffectiveTimeType.IMMEDIATE,
                NOW, "b".repeat(64), NOW);
        MaintenanceWorkflowOperation requestOperation = MaintenanceWorkflowOperation.create(
                "request-operation", MaintenanceWorkflowAction.REQUEST_EFFECT, "task-effect",
                request.evidenceVersion(), request.requestPayloadHash(), "EFFECTING",
                null, NOW, "operator-1");
        MaintenanceWorkflowTask requested = ready.requestEffect(request, requestOperation);
        MaintenanceSnapshotReference appliedSnapshot = new MaintenanceSnapshotReference(
                "snapshot://case-1/applied", "c".repeat(64), 8,
                OffsetDateTime.parse("2026-08-25T10:01:00+08:00"));
        MaintenancePolicyApplicationEvidence application = new MaintenancePolicyApplicationEvidence(
                "effect-request-1", "END-001", 7, 8, "d".repeat(64), appliedSnapshot,
                List.of(new MaintenanceAppliedFieldEvidence(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        PolicyFieldDataType.TEXT, "13900000000")),
                NOW.plusMinutes(1));
        MaintenanceWorkflowOperation appliedOperation = MaintenanceWorkflowOperation.create(
                "applied-operation", MaintenanceWorkflowAction.RECORD_POLICY_APPLICATION,
                "task-effect", application.evidenceVersion(), application.applicationHash(),
                "APPLIED", application.endorsementNo(), NOW.plusMinutes(1), "policy-service");
        MaintenanceWorkflowTask applied = requested.recordPolicyApplication(
                application, appliedOperation);

        handler.on(new MaintenanceWorkflowTaskTransitionedEvent(
                ID, requested, applied, null, null, appliedOperation.operationId(),
                appliedOperation.payloadHash(), appliedOperation.operatedAt(),
                appliedOperation.operatedBy(), "tenant-1"));

        assertEquals("13900000000", fieldView.getAppliedValue());
        ArgumentCaptor<MaintenanceSnapshotView> snapshotCaptor =
                ArgumentCaptor.forClass(MaintenanceSnapshotView.class);
        verify(snapshotViewRepository).save(snapshotCaptor.capture());
        assertEquals("snapshot://case-1/applied", snapshotCaptor.getValue().getAppliedStorageKey());
        assertEquals(8L, snapshotCaptor.getValue().getAppliedPolicyVersion());
    }

    private PolicyMaintenanceSnapshot policySnapshot() {
        MaintenanceSnapshotReference before = new MaintenanceSnapshotReference(
                "axon-event://policy/tenant-1/policy-1?version=7", "b".repeat(64), 7,
                OffsetDateTime.parse("2026-08-24T08:00:00Z"));
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P202608240001", CustomerId.of("customer-1"),
                "product-1", "product-v1", "plan-v1", PolicyStatus.EFFECTIVE, 7,
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"), before,
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13800000000")));
    }

    private MaintenanceWorkflowTaskTransitionedEvent quotedTransition(
            String taskId,
            String itemCode,
            String quoteId,
            String originalCalculationId,
            String replacementCalculationId,
            LocalDateTime quotedAt) {
        MaintenanceWorkflowTask ready = new MaintenanceWorkflowTask(
                taskId, itemCode, 0, 2, MaintenanceStepType.FEE_SETTLEMENT,
                MaintenanceStepMode.REQUIRED, null, MaintenanceWorkflowTaskStatus.READY);
        MaintenancePremiumQuoteEvidence quote = new MaintenancePremiumQuoteEvidence(
                MaintenancePremiumQuoteStatus.QUOTED, quoteId, "quote-v1", "a".repeat(64),
                originalCalculationId, "b".repeat(64), replacementCalculationId, "c".repeat(64),
                "plan-v1", "d".repeat(64), "e".repeat(64), "DEBIT 10 CNY; lines=1",
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("10.00"), "CNY",
                quotedAt, quotedAt.plusHours(24));
        MaintenanceWorkflowOperation operation = MaintenanceWorkflowOperation.create(
                "operation-" + quoteId, MaintenanceWorkflowAction.RECORD_PREMIUM_QUOTE,
                taskId, quote.evidenceVersion(), quote.contentHash(), quote.status().getCode(),
                quote.detailSummary(), quotedAt, "pricing-service");
        MaintenanceWorkflowTask quoted = ready.recordPremiumQuote(quote, operation);
        return new MaintenanceWorkflowTaskTransitionedEvent(
                ID, ready, quoted, null, null, operation.operationId(), operation.payloadHash(),
                operation.operatedAt(), operation.operatedBy(), "tenant-1");
    }

    private MaintenanceItemInstance item() {
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                "POLICY_INFO_CHANGE", "1.0.0", "保单基本信息变更",
                MaintenanceItemCategory.BASIC_INFORMATION, Set.of(MaintenanceChannel.MANUAL),
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), false);
        MaintenanceItemSelectionEvidence evidence = MaintenanceItemSelectionEvidence.authoritative(
                "configuration-1", "1.0.0", "d".repeat(64),
                "offering-1", "offering-v1", "e".repeat(64),
                OffsetDateTime.parse("2026-08-24T09:00:00+08:00"));
        return MaintenanceItemInstance.from(definition, evidence, NOW);
    }

    private MaintenanceFieldCatalogSnapshot fieldCatalog() {
        MaintenanceFieldDescriptorSnapshot descriptor = new MaintenanceFieldDescriptorSnapshot(
                "policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                "policy.field.holder.mobile", false, null, true, true, true, false,
                "POLICY_INFO_CHANGE", PolicyFieldSensitivityLevel.SENSITIVE,
                PolicyFieldMaskingPolicy.MOBILE, null);
        return new MaintenanceFieldCatalogSnapshot(
                "tenant-1", LocalDate.of(2026, 8, 1), "catalog-v1", "f".repeat(64),
                OffsetDateTime.parse("2026-08-24T09:01:00+08:00"),
                Map.of(descriptor.fieldCode(), descriptor));
    }
}
