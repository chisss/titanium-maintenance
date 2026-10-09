package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.effect.MaintenanceEffectApplicationInput;
import com.titanium.maintenance.application.model.effect.LoanDisbursementFact;
import com.titanium.maintenance.application.model.effect.LoanDisbursementOrder;
import com.titanium.maintenance.application.model.effect.LoanRepaymentFact;
import com.titanium.maintenance.application.model.effect.LoanRepaymentOrder;
import com.titanium.maintenance.application.model.effect.MaintenanceEffectApplicationResult;
import com.titanium.maintenance.application.model.effect.MaintenanceInvestmentSwitchInput;
import com.titanium.maintenance.application.model.effect.MaintenanceLoanDisbursementInput;
import com.titanium.maintenance.application.model.effect.MaintenanceLoanRepaymentInput;
import com.titanium.maintenance.application.model.effect.ReducedPaidUpFact;
import com.titanium.maintenance.application.model.effect.ReducedPaidUpOrder;
import com.titanium.maintenance.application.model.field.MaintenanceFieldConflictOperationResult;
import com.titanium.maintenance.command.FailMaintenanceCaseEffectCommand;
import com.titanium.maintenance.command.RecordMaintenanceCaseInvestmentSwitchCommand;
import com.titanium.maintenance.command.RecordMaintenanceCaseLoanDisbursementCommand;
import com.titanium.maintenance.command.RecordMaintenanceCaseLoanRepaymentCommand;
import com.titanium.maintenance.command.RecordMaintenanceCasePolicyApplicationCommand;
import com.titanium.maintenance.command.RecordMaintenanceCasePolicyReissueCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectCompensationCommand;
import com.titanium.maintenance.command.RequestMaintenanceCaseEffectCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceConflictException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort.ResolutionFact;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort.ResolutionLineFact;
import com.titanium.maintenance.port.document.PolicyReissueDocumentPort;
import com.titanium.maintenance.port.investment.InvestmentAccountSwitchPort;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCapabilityEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanRepaymentReceipt;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.ApplicationFact;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.AppliedField;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.AppliedSnapshot;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.FieldChange;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.reissue.PolicyReissueDocument;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

class MaintenanceEffectApplicationServiceTest {

    private CommandGateway commandGateway;
    private MaintenanceViewRepository maintenanceViewRepository;
    private MaintenanceWorkflowTaskViewRepository taskRepository;
    private MaintenanceFieldChangeViewRepository fieldRepository;
    private MaintenanceSnapshotViewRepository snapshotRepository;
    private MaintenanceRetroactivePeriodAdjustmentViewRepository periodAdjustmentRepository;
    private MaintenanceFieldConflictApplicationService fieldConflictApplicationService;
    private PolicyFieldCatalogPort fieldCatalogPort;
    private PolicyMaintenanceApplicationPort policyApplicationPort;
    private BillingRetroactivePeriodResolutionPort billingResolutionPort;
    private InvestmentAccountSwitchPort investmentAccountSwitchPort;
    private PolicyReissueDocumentPort policyReissueDocumentPort;
    private LoanDisbursementOrchestrator loanDisbursementOrchestrator;
    private LoanRepaymentOrchestrator loanRepaymentOrchestrator;
    private ReducedPaidUpOrchestrator reducedPaidUpOrchestrator;
    private MaintenanceEffectApplicationService service;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        maintenanceViewRepository = mock(MaintenanceViewRepository.class);
        taskRepository = mock(MaintenanceWorkflowTaskViewRepository.class);
        fieldRepository = mock(MaintenanceFieldChangeViewRepository.class);
        snapshotRepository = mock(MaintenanceSnapshotViewRepository.class);
        periodAdjustmentRepository = mock(MaintenanceRetroactivePeriodAdjustmentViewRepository.class);
        fieldConflictApplicationService = mock(MaintenanceFieldConflictApplicationService.class);
        fieldCatalogPort = mock(PolicyFieldCatalogPort.class);
        policyApplicationPort = mock(PolicyMaintenanceApplicationPort.class);
        billingResolutionPort = mock(BillingRetroactivePeriodResolutionPort.class);
        investmentAccountSwitchPort = mock(InvestmentAccountSwitchPort.class);
        policyReissueDocumentPort = mock(PolicyReissueDocumentPort.class);
        loanDisbursementOrchestrator = mock(LoanDisbursementOrchestrator.class);
        loanRepaymentOrchestrator = mock(LoanRepaymentOrchestrator.class);
        reducedPaidUpOrchestrator = mock(ReducedPaidUpOrchestrator.class);
        service = new MaintenanceEffectApplicationService(
                commandGateway, maintenanceViewRepository, taskRepository, fieldRepository,
                snapshotRepository, periodAdjustmentRepository, fieldConflictApplicationService, fieldCatalogPort,
                policyApplicationPort, billingResolutionPort, investmentAccountSwitchPort,
                policyReissueDocumentPort,
                loanDisbursementOrchestrator, loanRepaymentOrchestrator, reducedPaidUpOrchestrator);
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(fieldConflictApplicationService.refreshIfVersionChanged(any(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(null));
        visibleContext(true);
    }

    @Test
    void shouldStopBeforePolicyWhenRefreshDetectsConflict() {
        MaintenanceFieldChange conflict = MaintenanceFieldChange.propose(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        MaintenanceFieldValue.text("13800000000"),
                        MaintenanceFieldValue.text("13900000000"))
                .refreshCurrent(MaintenanceFieldValue.text("13700000000"));
        when(fieldConflictApplicationService.refreshIfVersionChanged(any(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(new MaintenanceFieldConflictOperationResult(
                        "effect-1:conflict-refresh:policy-v8", 8, "e".repeat(64), 1, List.of(conflict))));

        assertThrows(java.util.concurrent.CompletionException.class, () -> service.apply(input()).join());

        verify(policyApplicationPort, never()).apply(any());
        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldUseRefreshedVersionAndSnapshotWhenPolicyDriftHasNoConflict() {
        MaintenanceFieldChange refreshed = MaintenanceFieldChange.propose(
                "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                MaintenanceFieldValue.text("13800000000"),
                MaintenanceFieldValue.text("13900000000"));
        when(fieldConflictApplicationService.refreshIfVersionChanged(any(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(new MaintenanceFieldConflictOperationResult(
                        "effect-1:conflict-refresh:policy-v8", 8, "e".repeat(64), 0, List.of(refreshed))));
        when(policyApplicationPort.apply(any())).thenAnswer(invocation -> {
            PolicyMaintenanceApplicationPort.ApplicationRequest request = invocation.getArgument(0);
            assertEquals(8, request.expectedPolicyVersion());
            assertEquals("e".repeat(64), request.proposedSnapshotHash());
            return successfulPolicyFact(request);
        });

        service.apply(input()).join();

        verify(policyApplicationPort).apply(any());
    }

    @Test
    void shouldRebuildFailedRequestFromLatestSnapshotAfterRetry() {
        visibleContext(true);
        MaintenanceWorkflowTaskView retriedTask = effectTask(
                "effect-task-1", "POLICY_INFO_CHANGE", 0);
        retriedTask.setRetryCount(1);
        retriedTask.setEffectRequestId("stale-effect-request");
        retriedTask.setEffectExpectedPolicyVersion(7L);
        retriedTask.setEffectTimeType(EffectiveTimeType.IMMEDIATE);
        retriedTask.setEffectRequestedEffectiveAt(LocalDateTime.parse("2026-08-25T09:00:00"));
        retriedTask.setEffectProposedSnapshotHash("d".repeat(64));
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(retriedTask));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(retriedTask));
        MaintenanceSnapshotView refreshedSnapshot = new MaintenanceSnapshotView();
        refreshedSnapshot.setMaintenanceId("maintenance-1");
        refreshedSnapshot.setTenantId("tenant-1");
        refreshedSnapshot.setProposedPolicyVersion(8L);
        refreshedSnapshot.setProposedContentHash("e".repeat(64));
        when(snapshotRepository.findByMaintenanceIdAndTenantId("maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(refreshedSnapshot));
        when(policyApplicationPort.apply(any())).thenAnswer(invocation -> {
            PolicyMaintenanceApplicationPort.ApplicationRequest request = invocation.getArgument(0);
            assertEquals(8L, request.expectedPolicyVersion());
            assertEquals("e".repeat(64), request.proposedSnapshotHash());
            return successfulPolicyFact(request);
        });

        service.apply(input()).join();

        verify(fieldConflictApplicationService).refreshIfVersionChanged(any(), org.mockito.Mockito.eq(8L));
        verify(policyApplicationPort).apply(any());
    }

    @Test
    void shouldFreezeRequestCallPolicyAndRecordReceipt() {
        when(policyApplicationPort.apply(any())).thenAnswer(invocation -> {
            PolicyMaintenanceApplicationPort.ApplicationRequest request = invocation.getArgument(0);
            return new ApplicationFact(
                    request.requestId(), "END-001", request.expectedPolicyVersion(), 8,
                    "b".repeat(64),
                    new AppliedSnapshot(
                            "axon-event://policy/tenant-1/policy-1?version=8", "c".repeat(64), 8,
                            OffsetDateTime.parse("2026-08-25T10:00:00+08:00")),
                    List.of(new AppliedField(
                            "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                            "TEXT", "13900000000")),
                    LocalDateTime.parse("2026-08-25T10:00:00"));
        });

        var result = service.apply(input()).join();

        assertEquals("END-001", result.endorsementNo());
        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).send(commands.capture());
        assertEquals(RequestMaintenanceCaseEffectCommand.class, commands.getAllValues().get(0).getClass());
        RecordMaintenanceCasePolicyApplicationCommand recorded =
                (RecordMaintenanceCasePolicyApplicationCommand) commands.getAllValues().get(1);
        assertEquals("13900000000", recorded.evidence().appliedFields().getFirst().canonicalValue());
    }

    @Test
    void shouldRecordFailureWhenPolicyCallFails() {
        when(policyApplicationPort.apply(any())).thenThrow(new IllegalStateException("Policy unavailable"));

        assertThrows(java.util.concurrent.CompletionException.class, () -> service.apply(input()).join());

        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).send(commands.capture());
        assertEquals(FailMaintenanceCaseEffectCommand.class, commands.getAllValues().get(1).getClass());
    }

    @Test
    void shouldFailClosedWhenCatalogExecutionCapabilityIsDisabled() {
        visibleContext(false);

        assertThrows(MaintenanceValidationException.class, () -> service.apply(input()));

        verify(commandGateway, never()).send(any());
        verify(policyApplicationPort, never()).apply(any());
    }

    @Test
    void shouldFailClosedUntilRetroactiveImpactAnalysisIsAvailable() {
        MaintenanceView caseView = visibleContext(true);
        caseView.setEffectiveTimeType(EffectiveTimeType.RETROACTIVE);

        assertThrows(MaintenanceValidationException.class, () -> service.apply(input()));

        verify(commandGateway, never()).send(any());
        verify(policyApplicationPort, never()).apply(any());
    }

    @Test
    void shouldApplyRetroactiveMaintenanceWithOpenPeriodEvidence() {
        MaintenanceView caseView = retroactiveContext(false);
        when(policyApplicationPort.apply(any())).thenAnswer(invocation -> {
            PolicyMaintenanceApplicationPort.ApplicationRequest request = invocation.getArgument(0);
            assertEquals("RETROACTIVE", request.effectiveTimeType());
            assertEquals("billing-batch-1", request.retroactiveEvidence().billingBatchId());
            return successfulPolicyFact(request);
        });

        var result = service.apply(input()).join();

        assertEquals("END-RETROACTIVE", result.endorsementNo());
        verify(billingResolutionPort, never()).get( any());
        assertEquals(MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED,
                caseView.getRetroactivePeriodRecalculationStatus());
    }

    @Test
    void shouldReconcileClosedPeriodResolutionBeforeRetroactivePolicyCall() {
        retroactiveContext(true);
        when(billingResolutionPort.get( "billing-batch-1"))
                .thenReturn(resolutionFact());
        when(policyApplicationPort.apply(any())).thenAnswer(invocation ->
                successfulPolicyFact(invocation.getArgument(0)));

        service.apply(input()).join();

        verify(billingResolutionPort).get( "billing-batch-1");
        verify(policyApplicationPort).apply(any());
    }

    @Test
    void shouldRejectRetroactiveMaintenanceWithBlockingImpact() {
        MaintenanceView caseView = retroactiveContext(false);
        caseView.setRetroactiveImpactBlockingCount(1);

        assertThrows(MaintenanceValidationException.class, () -> service.apply(input()));

        verify(policyApplicationPort, never()).apply(any());
    }

    @Test
    void shouldSubmitMultipleEffectTasksToPolicyOnlyOnce() {
        MaintenanceWorkflowTaskView first = effectTask("effect-task-1", "POLICY_INFO_CHANGE", 0);
        MaintenanceWorkflowTaskView second = effectTask("effect-task-2", "POLICY_SUSPENSION", 1);
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(first));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(first, second));
        when(policyApplicationPort.apply(any())).thenAnswer(invocation -> {
            PolicyMaintenanceApplicationPort.ApplicationRequest request = invocation.getArgument(0);
            assertEquals(PolicyMaintenanceAction.SUSPEND, request.stateAction());
            return new ApplicationFact(
                    request.requestId(), "END-ATOMIC", request.expectedPolicyVersion(), 8,
                    "b".repeat(64), appliedSnapshot(),
                    List.of(new AppliedField(
                            "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                            "TEXT", "13900000000")),
                    LocalDateTime.parse("2026-08-25T10:00:00"),
                    PolicyMaintenanceAction.SUSPEND, "EFFECTIVE", "SUSPENDED");
        });

        var result = service.apply(input()).join();

        assertEquals("END-ATOMIC", result.endorsementNo());
        verify(policyApplicationPort).apply(any());
        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).send(commands.capture());
        RequestMaintenanceCaseEffectCommand requested =
                (RequestMaintenanceCaseEffectCommand) commands.getAllValues().getFirst();
        assertEquals(List.of("effect-task-1", "effect-task-2"), requested.taskIds());
        RecordMaintenanceCasePolicyApplicationCommand recorded =
                (RecordMaintenanceCasePolicyApplicationCommand) commands.getAllValues().get(1);
        assertEquals(requested.taskIds(), recorded.taskIds());
    }

    @Test
    void shouldApplyStateOnlyMaintenanceWithoutFieldCatalogLookup() {
        MaintenanceWorkflowTaskView task = effectTask("effect-task-1", "POLICY_SUSPENSION", 0);
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(task));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(task));
        when(fieldRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of());
        MaintenanceSnapshotView snapshot = new MaintenanceSnapshotView();
        snapshot.setMaintenanceId("maintenance-1");
        snapshot.setTenantId("tenant-1");
        snapshot.setBeforePolicyVersion(7L);
        snapshot.setBeforeContentHash("a".repeat(64));
        when(snapshotRepository.findByMaintenanceIdAndTenantId("maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(snapshot));
        when(policyApplicationPort.apply(any())).thenAnswer(invocation -> {
            PolicyMaintenanceApplicationPort.ApplicationRequest request = invocation.getArgument(0);
            assertEquals(7L, request.expectedPolicyVersion());
            assertEquals("a".repeat(64), request.proposedSnapshotHash());
            return new ApplicationFact(
                    request.requestId(), "END-STATE", request.expectedPolicyVersion(), 8,
                    "b".repeat(64), appliedSnapshot(), List.of(),
                    LocalDateTime.parse("2026-08-25T10:00:00"),
                    PolicyMaintenanceAction.SUSPEND, "EFFECTIVE", "SUSPENDED");
        });

        service.apply(input()).join();

        verify(fieldCatalogPort, never()).getCatalog(any());
        verify(policyApplicationPort).apply(any());
    }

    @Test
    void shouldRecordIndependentCompensationWhenPolicySucceededButReceiptWriteFailed() {
        when(policyApplicationPort.apply(any())).thenAnswer(invocation -> {
            PolicyMaintenanceApplicationPort.ApplicationRequest request = invocation.getArgument(0);
            return new ApplicationFact(
                    request.requestId(), "END-COMPENSATE", request.expectedPolicyVersion(), 8,
                    "b".repeat(64), appliedSnapshot(),
                    List.of(new AppliedField(
                            "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                            "TEXT", "13900000000")),
                    LocalDateTime.parse("2026-08-25T10:00:00"));
        });
        when(commandGateway.send(any())).thenAnswer(invocation -> {
            Object command = invocation.getArgument(0);
            if (command instanceof RecordMaintenanceCasePolicyApplicationCommand) {
                return CompletableFuture.failedFuture(new IllegalStateException("event store unavailable"));
            }
            return CompletableFuture.completedFuture(null);
        });

        assertThrows(java.util.concurrent.CompletionException.class, () -> service.apply(input()).join());

        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(3)).send(commands.capture());
        assertEquals(RequestMaintenanceCaseEffectCommand.class, commands.getAllValues().get(0).getClass());
        assertEquals(RecordMaintenanceCasePolicyApplicationCommand.class, commands.getAllValues().get(1).getClass());
        RecordMaintenanceEffectCompensationCommand compensation =
                (RecordMaintenanceEffectCompensationCommand) commands.getAllValues().get(2);
        assertEquals("END-COMPENSATE", compensation.evidence().endorsementNo());
        assertEquals(8, compensation.evidence().actualPolicyVersion());
    }

    @Test
    void shouldRecoverCompletedPolicyApplicationWithoutSideEffects() {
        MaintenanceWorkflowTaskView task = completedEffectTask(
                "effect-task-1", "END-RECOVERED", "b".repeat(64));
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(task));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(task));

        var result = service.apply(input()).join();

        assertEquals("effect-case-1", result.requestId());
        assertEquals("END-RECOVERED", result.endorsementNo());
        assertEquals(8, result.actualPolicyVersion());
        assertEquals("b".repeat(64), result.applicationHash());
        assertEquals(LocalDateTime.parse("2026-08-25T10:00:00"), result.appliedAt());
        verify(fieldConflictApplicationService, never()).refreshIfVersionChanged(any(), anyLong());
        verify(policyApplicationPort, never()).apply(any());
        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldRejectCompletedEffectTasksWithDifferentPolicyReceipts() {
        MaintenanceWorkflowTaskView first = completedEffectTask(
                "effect-task-1", "END-001", "b".repeat(64));
        MaintenanceWorkflowTaskView second = completedEffectTask(
                "effect-task-2", "END-002", "c".repeat(64));
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(first));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(first, second));

        assertThrows(MaintenanceValidationException.class, () -> service.apply(input()));

        verify(policyApplicationPort, never()).apply(any());
        verify(commandGateway, never()).send(any());
    }

    private MaintenanceView visibleContext(boolean executionSupported) {
        MaintenanceView caseView = new MaintenanceView();
        caseView.setMaintenanceId("maintenance-1");
        caseView.setPolicyId("policy-1");
        caseView.setTenantId("tenant-1");
        caseView.setIndependentCase(true);
        caseView.setInitializationCompleted(true);
        caseView.setEffectiveTimeType(EffectiveTimeType.IMMEDIATE);
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(caseView));

        MaintenanceWorkflowTaskView task = effectTask("effect-task-1", "POLICY_INFO_CHANGE", 0);
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(task));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(task));

        MaintenanceSnapshotView snapshot = new MaintenanceSnapshotView();
        snapshot.setMaintenanceId("maintenance-1");
        snapshot.setTenantId("tenant-1");
        snapshot.setProposedPolicyVersion(7L);
        snapshot.setProposedContentHash("a".repeat(64));
        when(snapshotRepository.findByMaintenanceIdAndTenantId("maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(snapshot));

        MaintenanceFieldChangeView field = new MaintenanceFieldChangeView();
        field.setItemCode("POLICY_INFO_CHANGE");
        field.setObjectId("policy-1");
        field.setFieldCode("policy.holder.mobile");
        field.setDataType(PolicyFieldDataType.TEXT);
        field.setProposedValue("13900000000");
        field.setConflictStatus(MaintenanceFieldConflictStatus.NONE);
        when(fieldRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(field));

        PolicyFieldCapabilityEvidence capability = new PolicyFieldCapabilityEvidence(
                true, true, true, executionSupported, false, "POLICY_INFO_CHANGE");
        PolicyFieldDescriptorEvidence descriptor = new PolicyFieldDescriptorEvidence(
                "policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER,
                PolicyFieldValueType.TEXT, "policy.field.holder.mobile", false, null,
                capability, PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE, null,
                new FieldAnalysisCapability(true, false, false, true));
        when(fieldCatalogPort.getCatalog(any())).thenReturn(new PolicyFieldCatalogEvidence(
                "tenant-1", null, null, LocalDate.now(), false, "2026.08.25.1", "d".repeat(64),
                List.of(descriptor)));
        return caseView;
    }

    private MaintenanceView retroactiveContext(boolean closedPeriod) {
        MaintenanceView view = visibleContext(true);
        view.setEffectiveTimeType(EffectiveTimeType.RETROACTIVE);
        view.setSpecificEffectiveDate(LocalDateTime.of(2026, 7, 1, 0, 0));
        view.setRetroactiveImpactAnalysisId("analysis-1");
        view.setRetroactiveImpactAnalysisVersion(1);
        view.setRetroactiveImpactStatus(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED);
        view.setRetroactiveImpactBlockingCount(0);
        view.setRetroactiveImpactPendingCount(0);
        view.setRetroactiveImpactResultHash("a".repeat(64));
        view.setRetroactivePeriodRecalculationId("period-recalculation-1");
        view.setRetroactivePeriodRecalculationVersion(1);
        view.setRetroactivePeriodAnalysisId("analysis-1");
        view.setRetroactivePeriodAnalysisVersion(1);
        view.setRetroactivePeriodAnalysisResultHash("a".repeat(64));
        view.setRetroactiveProductRecalculationId("product-recalculation-1");
        view.setRetroactiveProductRecalculationVersion("PERIOD_V1");
        view.setRetroactiveProductInputHash("b".repeat(64));
        view.setRetroactiveProductResultHash("c".repeat(64));
        view.setRetroactivePeriodCount(1);
        view.setRetroactiveBillingBatchId("billing-batch-1");
        view.setRetroactiveBillingResultHash("d".repeat(64));
        MaintenanceRetroactivePeriodAdjustmentView period = new MaintenanceRetroactivePeriodAdjustmentView();
        period.setPeriodId("BILLING:bill-1");
        period.setBillingStatus(closedPeriod ? "CLOSED_PERIOD_REVIEW" : "POSTED");
        period.setProductResultHash("e".repeat(64));
        period.setBillingResultHash("f".repeat(64));
        when(periodAdjustmentRepository
                .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                        "tenant-1", "maintenance-1", "period-recalculation-1"))
                .thenReturn(List.of(period));
        if (!closedPeriod) {
            view.setRetroactivePeriodRecalculationStatus(
                    MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED);
            view.setRetroactiveBillingStatus("POSTED");
            return view;
        }
        view.setRetroactivePeriodRecalculationStatus(
                MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED);
        view.setRetroactiveBillingStatus("REVIEW_REQUIRED");
        view.setRetroactiveBillingReviewCount(1);
        view.setRetroactivePeriodResolutionStatus(MaintenanceRetroactivePeriodResolutionStatus.COMPLETED);
        view.setRetroactiveBillingResolutionId("billing-resolution-1");
        view.setRetroactivePeriodResolutionSourceBatchHash("d".repeat(64));
        view.setRetroactivePeriodResolutionTargetPeriod("2026-08");
        view.setRetroactivePeriodResolutionResolvedLineCount(1);
        view.setRetroactivePeriodResolutionResultHash("e".repeat(64));
        return view;
    }

    private ApplicationFact successfulPolicyFact(
            PolicyMaintenanceApplicationPort.ApplicationRequest request) {
        long actualVersion = request.expectedPolicyVersion() + 1;
        return new ApplicationFact(
                request.requestId(), "END-RETROACTIVE", request.expectedPolicyVersion(), actualVersion,
                "f".repeat(64), new AppliedSnapshot(
                        "axon-event://policy/tenant-1/policy-1?version=" + actualVersion,
                        "c".repeat(64), actualVersion,
                        OffsetDateTime.parse("2026-08-25T10:00:00+08:00")),
                List.of(new AppliedField(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        "TEXT", "13900000000")),
                LocalDateTime.parse("2026-08-25T10:00:00"), PolicyMaintenanceAction.NONE,
                null, null, request.retroactiveEvidence());
    }

    private ResolutionFact resolutionFact() {
        return new ResolutionFact(
                "billing-resolution-1", "mrr-request-1", "billing-batch-1", "tenant-1",
                "maintenance-1", "policy-1", "d".repeat(64), YearMonth.of(2026, 8),
                "COMPLETED", 1, "a".repeat(64), "e".repeat(64),
                "结转至当前开放期间", "operator-1", LocalDateTime.of(2026, 8, 26, 14, 0),
                List.of(new ResolutionLineFact(
                        "BILLING:bill-1", YearMonth.of(2026, 7), YearMonth.of(2026, 8),
                        MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"), "CNY",
                        "posting-1", "f".repeat(64), "e".repeat(64))));
    }

    // ==================== 账户转换（FUND_SWITCH）生效出口 ====================

    @Test
    void shouldRouteInvestmentSwitchCaseToInvestmentPortWithoutPolicyApplication() {
        investmentSwitchContext(MaintenanceWorkflowTaskStatus.READY);
        when(investmentAccountSwitchPort.switchByPolicy(any())).thenReturn(switchFact());

        MaintenanceEffectApplicationResult result = service.apply(switchInput()).join();

        verify(policyApplicationPort, never()).apply(any());
        ArgumentCaptor<InvestmentAccountSwitchPort.InvestmentSwitchRequest> switchCaptor =
                ArgumentCaptor.forClass(InvestmentAccountSwitchPort.InvestmentSwitchRequest.class);
        verify(investmentAccountSwitchPort).switchByPolicy(switchCaptor.capture());
        assertEquals("policy-1", switchCaptor.getValue().policyId(), "账户按保单寻址");
        assertEquals("tenant-1", switchCaptor.getValue().tenantId());
        assertEquals(0, switchCaptor.getValue().switchOutUnits().compareTo(new BigDecimal("100")),
                "转出单位应取自生效请求");
        List<Object> commands = capturedCommands(2);
        RequestMaintenanceCaseEffectCommand frozen = (RequestMaintenanceCaseEffectCommand) commands.get(0);
        RecordMaintenanceCaseInvestmentSwitchCommand receipt =
                (RecordMaintenanceCaseInvestmentSwitchCommand) commands.get(1);
        assertEquals("tenant-1", frozen.tenantId());
        assertEquals("tenant-1", receipt.tenantId());
        assertEquals("account-1", receipt.evidence().accountId());
        assertEquals(new BigDecimal("1.25000000"), receipt.evidence().unitPrice());
        assertNull(result.endorsementNo(), "账户转换不改保单，无批单号");
        assertEquals(receipt.evidence().contentHash(), result.applicationHash());
        assertEquals(receipt.evidence().switchedAt(), result.appliedAt());
    }

    @Test
    void shouldRejectInvestmentSwitchCaseWithoutParameters() {
        investmentSwitchContext(MaintenanceWorkflowTaskStatus.READY);

        MaintenanceValidationException error = assertValidationFailure(() -> service.apply(input()).join());

        assertTrue(error.getMessage().contains("investmentSwitch"), error.getMessage());
        verify(investmentAccountSwitchPort, never()).switchByPolicy(any());
        verify(policyApplicationPort, never()).apply(any());
        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldRejectInvestmentSwitchItemMixedWithOtherItems() {
        investmentSwitchContext(MaintenanceWorkflowTaskStatus.READY);
        MaintenanceWorkflowTaskView other = effectTask("effect-task-2", "POLICY_INFO_CHANGE", 1);
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(effectTask("effect-task-1", "FUND_SWITCH", 0), other));
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(effectTask("effect-task-1", "FUND_SWITCH", 0)));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.apply(switchInput()).join());

        assertTrue(error.getMessage().contains("investmentSwitch"), error.getMessage());
        verify(investmentAccountSwitchPort, never()).switchByPolicy(any());
    }

    @Test
    void shouldRejectInvestmentSwitchCaseOnScheduledEntry() {
        investmentSwitchContext(MaintenanceWorkflowTaskStatus.READY);
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(schedulableSwitchCase()));

        assertThrows(java.util.concurrent.CompletionException.class,
                () -> service.applyScheduled(switchInput(), LocalDateTime.parse("2026-09-01T00:00:00")).join());

        verify(investmentAccountSwitchPort, never()).switchByPolicy(any());
    }

    @Test
    void shouldFailClosedWhenPolicyHasNoInvestmentAccount() {
        investmentSwitchContext(MaintenanceWorkflowTaskStatus.READY);
        when(investmentAccountSwitchPort.switchByPolicy(any())).thenReturn(null);

        assertThrows(java.util.concurrent.CompletionException.class, () -> service.apply(switchInput()).join());

        FailMaintenanceCaseEffectCommand failure =
                (FailMaintenanceCaseEffectCommand) capturedCommands(2).get(1);
        assertEquals("INVESTMENT_SWITCH_FAILED", failure.failureCode());
        verify(commandGateway, never()).send(any(RecordMaintenanceCaseInvestmentSwitchCommand.class));
    }

    @Test
    void shouldRejectSwitchRetryWhenFrozenRequestPayloadDiffers() {
        MaintenanceWorkflowTaskView waiting = investmentSwitchContext(
                MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL);
        waiting.setEffectRequestId("effect-case-1");

        MaintenanceValidationException error = assertValidationFailure(() -> service.apply(switchInput()).join());

        assertTrue(error.getMessage().contains("investmentSwitch"), error.getMessage());
        verify(investmentAccountSwitchPort, never()).switchByPolicy(any());
    }

    @Test
    void shouldReplaySwitchReceiptForCompletedSwitchCase() {
        MaintenanceWorkflowTaskView completed = investmentSwitchContext(
                MaintenanceWorkflowTaskStatus.COMPLETED);
        completed.setEffectRequestId("effect-case-1");
        completed.setEffectRequestHash("a".repeat(64));
        completed.setEffectExpectedPolicyVersion(7L);
        completed.setEffectTimeType(EffectiveTimeType.IMMEDIATE);
        completed.setEffectRequestedEffectiveAt(LocalDateTime.parse("2026-08-25T09:59:59"));
        completed.setEffectProposedSnapshotHash("d".repeat(64));
        completed.setSwitchAccountId("account-1");
        completed.setSwitchEvidenceHash("f".repeat(64));
        completed.setSwitchUnitPrice(new BigDecimal("1.25000000"));
        completed.setSwitchTotalUnits(new BigDecimal("800.00000000"));
        completed.setSwitchAccountValue(new BigDecimal("1000.00000000"));
        completed.setSwitchCurrency("CNY");
        completed.setSwitchSwitchedAt(LocalDateTime.parse("2026-08-25T10:00:00"));

        MaintenanceEffectApplicationResult result = service.apply(switchInput()).join();

        assertEquals("effect-case-1", result.requestId());
        assertNull(result.endorsementNo());
        assertEquals("f".repeat(64), result.applicationHash());
        assertEquals(LocalDateTime.parse("2026-08-25T10:00:00"), result.appliedAt());
        verifyNoInteractions(investmentAccountSwitchPort);
    }

    // ==================== 保单贷款（POLICY_LOAN）生效出口 ====================

    @Test
    void shouldRouteLoanCaseToLoanOrchestratorWithoutPolicyApplication() {
        loanContext(MaintenanceWorkflowTaskStatus.READY);
        when(loanDisbursementOrchestrator.disburse(any())).thenReturn(loanFact());

        MaintenanceEffectApplicationResult result = service.apply(loanInput()).join();

        verify(policyApplicationPort, never()).apply(any());
        verify(investmentAccountSwitchPort, never()).switchByPolicy(any());
        ArgumentCaptor<LoanDisbursementOrder> orderCaptor =
                ArgumentCaptor.forClass(LoanDisbursementOrder.class);
        verify(loanDisbursementOrchestrator).disburse(orderCaptor.capture());
        assertEquals("policy-1", orderCaptor.getValue().policyId(), "贷款按保单寻址");
        assertEquals("tenant-1", orderCaptor.getValue().tenantId());
        assertEquals(0, orderCaptor.getValue().requestedAmount()
                        .compareTo(new BigDecimal("5000")), "申请金额应取自贷款参数");
        List<Object> commands = capturedCommands(2);
        RequestMaintenanceCaseEffectCommand frozen = (RequestMaintenanceCaseEffectCommand) commands.get(0);
        RecordMaintenanceCaseLoanDisbursementCommand receipt =
                (RecordMaintenanceCaseLoanDisbursementCommand) commands.get(1);
        assertEquals("tenant-1", frozen.tenantId());
        assertEquals("tenant-1", receipt.tenantId());
        assertEquals("LOAN" + "a".repeat(28), receipt.evidence().loanId());
        assertEquals(0, receipt.evidence().loanAmount().compareTo(new BigDecimal("5000")));
        assertEquals("POSTING-9", receipt.evidence().billingPostingId());
        assertEquals("PAYOUT-7", receipt.evidence().paymentOrderId());
        assertNull(result.endorsementNo(), "贷款不改保单，无批单号");
        assertEquals(receipt.evidence().contentHash(), result.applicationHash());
        assertEquals(receipt.evidence().disbursedAt(), result.appliedAt());
    }

    @Test
    void shouldRejectLoanCaseWithoutParameters() {
        loanContext(MaintenanceWorkflowTaskStatus.READY);

        MaintenanceValidationException error = assertValidationFailure(() -> service.apply(input()).join());

        assertTrue(error.getMessage().contains("loanDisbursement"), error.getMessage());
        verify(loanDisbursementOrchestrator, never()).disburse(any());
        verify(policyApplicationPort, never()).apply(any());
        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldRejectLoanItemMixedWithOtherItems() {
        loanContext(MaintenanceWorkflowTaskStatus.READY);
        MaintenanceWorkflowTaskView other = effectTask("effect-task-2", "POLICY_INFO_CHANGE", 1);
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(effectTask("effect-task-1", "POLICY_LOAN", 0), other));
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(effectTask("effect-task-1", "POLICY_LOAN", 0)));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.apply(loanInput()).join());

        assertTrue(error.getMessage().contains("loanDisbursement"), error.getMessage());
        verify(loanDisbursementOrchestrator, never()).disburse(any());
    }

    @Test
    void shouldRejectLoanCaseOnScheduledEntry() {
        loanContext(MaintenanceWorkflowTaskStatus.READY);
        MaintenanceView scheduled = schedulableSwitchCase();
        scheduled.setProductId("product-1");
        scheduled.setCustomerId("customer-1");
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(scheduled));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.applyScheduled(loanInput(), LocalDateTime.parse("2026-09-01T00:00:00")).join());
        assertTrue(error.getMessage().contains("保单贷款"), error.getMessage());

        verify(loanDisbursementOrchestrator, never()).disburse(any());
    }

    @Test
    void shouldRejectLoanRetryWhenFrozenRequestPayloadDiffers() {
        MaintenanceWorkflowTaskView waiting = loanContext(
                MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL);
        waiting.setEffectRequestId("effect-case-1");
        // 已冻结请求的哈希是「首次申请额」的摘要；重试改用其它金额即载荷漂移
        waiting.setEffectRequestHash("b".repeat(64));

        MaintenanceValidationException error = assertValidationFailure(() -> service.apply(loanInput()).join());

        assertTrue(error.getMessage().contains("loanDisbursement"), error.getMessage());
        verify(loanDisbursementOrchestrator, never()).disburse(any());
    }

    @Test
    void shouldResumeLoanExecutionWhenFrozenRequestPayloadMatches() {
        MaintenanceWorkflowTaskView waiting = loanContext(
                MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL);
        waiting.setEffectRequestId("effect-case-1");
        // 冻结请求哈希 = 本次请求摘要（同租户同保单同金额），重试直接续跑回执，不再重复冻结
        waiting.setEffectRequestHash(new LoanDisbursementOrder(
                "tenant-1", "maintenance-1", "policy-1", "product-1", "customer-1",
                "operator-1", new BigDecimal("5000"), LocalDateTime.parse("2026-09-28T12:00:00"))
                .requestPayloadHash());
        when(loanDisbursementOrchestrator.disburse(any())).thenReturn(loanFact());

        service.apply(loanInput()).join();

        List<Object> commands = capturedCommands(1);
        assertInstanceOf(RecordMaintenanceCaseLoanDisbursementCommand.class, commands.get(0),
                "哈希一致的重试不得重新派发冻结命令（RequestMaintenanceCaseEffectCommand 幂等语义不得被绕过）");
        verify(loanDisbursementOrchestrator).disburse(any());
    }

    @Test
    void shouldReplayLoanReceiptForCompletedLoanCase() {
        MaintenanceWorkflowTaskView completed = loanContext(
                MaintenanceWorkflowTaskStatus.COMPLETED);
        completed.setEffectRequestId("effect-case-1");
        completed.setEffectRequestHash("a".repeat(64));
        completed.setEffectExpectedPolicyVersion(7L);
        completed.setEffectTimeType(EffectiveTimeType.IMMEDIATE);
        completed.setEffectRequestedEffectiveAt(LocalDateTime.parse("2026-08-25T09:59:59"));
        completed.setEffectProposedSnapshotHash("d".repeat(64));
        completed.setLoanId("LOAN" + "a".repeat(28));
        completed.setLastEvidenceHash("f".repeat(64));
        completed.setLoanDisbursedAt(LocalDateTime.parse("2026-08-25T10:00:00"));

        MaintenanceEffectApplicationResult result = service.apply(loanInput()).join();

        assertEquals("effect-case-1", result.requestId());
        assertNull(result.endorsementNo());
        assertEquals("f".repeat(64), result.applicationHash());
        assertEquals(LocalDateTime.parse("2026-08-25T10:00:00"), result.appliedAt());
        verifyNoInteractions(loanDisbursementOrchestrator);
    }

    // ==================== 减额缴清（REDUCED_PAID_UP）生效出口 ====================

    @Test
    void shouldRouteReducedPaidUpCaseToPolicyWithDerivedFieldChanges() {
        reducedPaidUpContext(MaintenanceWorkflowTaskStatus.READY);
        when(reducedPaidUpOrchestrator.calculate(any())).thenReturn(reducedPaidUpFact());
        when(policyApplicationPort.apply(any())).thenAnswer(
                invocation -> successfulPolicyFact(invocation.getArgument(0)));

        service.apply(input()).join();

        ArgumentCaptor<ReducedPaidUpOrder> orderCaptor = ArgumentCaptor.forClass(ReducedPaidUpOrder.class);
        verify(reducedPaidUpOrchestrator).calculate(orderCaptor.capture());
        assertEquals("tenant-1", orderCaptor.getValue().tenantId());
        assertEquals("maintenance-1", orderCaptor.getValue().maintenanceId());
        assertEquals("policy-1", orderCaptor.getValue().policyId());
        assertEquals("product-1", orderCaptor.getValue().productId(), "产品ID取自案件读模型");
        assertNotNull(orderCaptor.getValue().businessTime(), "费率版本锚定的业务时点不得为空");
        ArgumentCaptor<PolicyMaintenanceApplicationPort.ApplicationRequest> requestCaptor =
                ArgumentCaptor.forClass(PolicyMaintenanceApplicationPort.ApplicationRequest.class);
        verify(policyApplicationPort).apply(requestCaptor.capture());
        List<FieldChange> changes = requestCaptor.getValue().changes();
        assertEquals(2, changes.size(), "减额缴清派生两条字段变更");
        assertEquals(new FieldChange("REDUCED_PAID_UP", "PP-MAIN", "policy.coverage.sumInsured",
                        "DECIMAL", "11000.00"), changes.get(0),
                "保额变更的 objectId 必须是主险段标识（执行器逐字比对），值为新保额");
        assertEquals(new FieldChange("REDUCED_PAID_UP", "policy-1", "policy.payment.method",
                        "ENUM", "SINGLE_PAYMENT"), changes.get(1),
                "缴费方式变更的 objectId 必须是保单ID，值为趸缴");
        assertEquals(PolicyMaintenanceAction.NONE, requestCaptor.getValue().stateAction(),
                "减额缴清只改合同要素，不驱动保单状态迁移");
        verify(fieldCatalogPort, never()).getCatalog(any());
        List<Object> commands = capturedCommands(2);
        assertInstanceOf(RequestMaintenanceCaseEffectCommand.class, commands.get(0));
        assertInstanceOf(RecordMaintenanceCasePolicyApplicationCommand.class, commands.get(1),
                "减额缴清无独立业务凭证，权威回执复用 Policy 生效回执");
    }

    @Test
    @DisplayName("减额不成立（新保额超原保额）：携 71 段错误码失败关闭，且不触达 Policy 域")
    void shouldRecordFailureWhenReductionIsInvalid() {
        reducedPaidUpContext(MaintenanceWorkflowTaskStatus.READY);
        when(reducedPaidUpOrchestrator.calculate(any())).thenThrow(new MaintenanceValidationException(
                MaintenanceErrorCode.MAINTENANCE_REDUCED_PAID_UP_EXCEEDS_SUM_INSURED,
                "ReducedPaidUpOrchestrator", "减额缴清新保额超过原保额，不构成减额"));

        MaintenanceValidationException error = assertValidationFailure(() -> service.apply(input()).join());

        assertEquals(MaintenanceErrorCode.MAINTENANCE_REDUCED_PAID_UP_EXCEEDS_SUM_INSURED.getCode(),
                error.getErrorCodeValue(),
                "必须原样透出 71007012（红线 19：异常携带 BaseErrorCode 枚举）");
        verify(policyApplicationPort, never()).apply(any());
        List<Object> commands = capturedCommands(1);
        assertEquals("REDUCED_PAID_UP_FAILED",
                ((FailMaintenanceCaseEffectCommand) commands.getFirst()).failureCode(),
                "核定失败先于任何写入，只留失败事实");
    }

    /**
     * 记录失败事实的通道自身失败时，原始业务拒绝仍须原样透出。
     * <p>
     * 🔴 反例背景（g04-05 AC-06 真机暴露）：原实现用 {@code send(...).thenCompose(...)} 串联，失败命令一被拒，
     * 原始 {@code 71007012} 即被记录通道的 {@code 71000004} 完全取代——调用方读到的是状态机错误，
     * 真正的业务原因**既不落库也无处可查**。本用例锁死「原始病因优先」，并要求被掩盖者以 suppressed 保留。
     * </p>
     */
    @Test
    @DisplayName("记录失败事实本身失败：原始业务拒绝仍须透出（不得被记录通道错误码取代）")
    void shouldSurfaceOriginalFailureWhenFailureRecordingFails() {
        reducedPaidUpContext(MaintenanceWorkflowTaskStatus.READY);
        when(reducedPaidUpOrchestrator.calculate(any())).thenThrow(new MaintenanceValidationException(
                MaintenanceErrorCode.MAINTENANCE_REDUCED_PAID_UP_EXCEEDS_SUM_INSURED,
                "ReducedPaidUpOrchestrator", "减额缴清新保额超过原保额，不构成减额"));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.failedFuture(
                new MaintenanceConflictException(
                        "MaintenanceWorkflowTask", "status", "当前生效任务状态不允许记录生效失败")));

        MaintenanceValidationException error = assertValidationFailure(() -> service.apply(input()).join());

        assertEquals(MaintenanceErrorCode.MAINTENANCE_REDUCED_PAID_UP_EXCEEDS_SUM_INSURED.getCode(),
                error.getErrorCodeValue(),
                "记录通道的错误不得掩盖原始业务拒绝（71007012）");
        assertEquals(1, error.getSuppressed().length, "被掩盖的记录通道错误须作为 suppressed 保留");
    }

    @Test
    void shouldRejectReducedPaidUpItemMixedWithOtherItems() {
        reducedPaidUpContext(MaintenanceWorkflowTaskStatus.READY);
        MaintenanceWorkflowTaskView other = effectTask("effect-task-2", "POLICY_INFO_CHANGE", 1);
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(effectTask("effect-task-1", "REDUCED_PAID_UP", 0), other));
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(effectTask("effect-task-1", "REDUCED_PAID_UP", 0)));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.apply(input()).join());

        assertTrue(error.getMessage().contains("reducedPaidUp"), error.getMessage());
        verify(reducedPaidUpOrchestrator, never()).calculate(any());
    }

    @Test
    void shouldRejectReducedPaidUpCaseOnScheduledEntry() {
        reducedPaidUpContext(MaintenanceWorkflowTaskStatus.READY);
        MaintenanceView scheduled = schedulableSwitchCase();
        scheduled.setProductId("product-1");
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(scheduled));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.applyScheduled(
                        input(), LocalDateTime.parse("2026-09-01T00:00:00")).join());

        assertTrue(error.getMessage().contains("减额缴清"), error.getMessage());
        verify(reducedPaidUpOrchestrator, never()).calculate(any());
    }

    @Test
    @DisplayName("追溯生效的减额缴清在取追溯证据之前即被拒（时点闸口前置于追溯闸口）")
    void shouldRejectRetroactiveReducedPaidUpBeforeRetroactiveEvidenceLookup() {
        MaintenanceView caseView = reducedPaidUpContext(MaintenanceWorkflowTaskStatus.READY);
        caseView.setEffectiveTimeType(EffectiveTimeType.RETROACTIVE);
        caseView.setSpecificEffectiveDate(LocalDateTime.parse("2026-08-01T00:00:00"));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.apply(input()).join());

        assertTrue(error.getMessage().contains("减额缴清案件仅支持立即生效"), error.getMessage());
        verifyNoInteractions(billingResolutionPort);
        verify(reducedPaidUpOrchestrator, never()).calculate(any());
    }

    // ==================== 保单贷款还款（LOAN_REPAYMENT）生效出口 ====================

    @Test
    void shouldRouteLoanRepaymentCaseToRepaymentOrchestratorWithoutPolicyApplication() {
        loanRepaymentContext(MaintenanceWorkflowTaskStatus.READY);
        when(loanRepaymentOrchestrator.repay(any())).thenReturn(loanRepaymentFact());

        MaintenanceEffectApplicationResult result = service.apply(loanRepaymentInput()).join();

        verify(policyApplicationPort, never()).apply(any());
        verify(investmentAccountSwitchPort, never()).switchByPolicy(any());
        verify(loanDisbursementOrchestrator, never()).disburse(any());
        ArgumentCaptor<LoanRepaymentOrder> orderCaptor =
                ArgumentCaptor.forClass(LoanRepaymentOrder.class);
        verify(loanRepaymentOrchestrator).repay(orderCaptor.capture());
        assertEquals("policy-1", orderCaptor.getValue().policyId(), "还款按保单寻址");
        assertEquals("LOAN001", orderCaptor.getValue().loanId(), "目标明细由调用方指名，不做隐式推断");
        assertEquals(0, orderCaptor.getValue().repaymentAmount().compareTo(new BigDecimal("911.10")),
                "还款额应取自还款参数");
        List<Object> commands = capturedCommands(2);
        RequestMaintenanceCaseEffectCommand frozen = (RequestMaintenanceCaseEffectCommand) commands.get(0);
        RecordMaintenanceCaseLoanRepaymentCommand receipt =
                (RecordMaintenanceCaseLoanRepaymentCommand) commands.get(1);
        assertEquals("tenant-1", frozen.tenantId());
        assertEquals("tenant-1", receipt.tenantId());
        assertEquals("RPAY" + "a".repeat(28), receipt.evidence().repaymentId());
        assertEquals(0, receipt.evidence().interestPortion().compareTo(new BigDecimal("11.10")));
        assertEquals("POSTING-9", receipt.evidence().billingPostingId());
        assertNull(result.endorsementNo(), "还款不改保单，无批单号");
        assertEquals(receipt.evidence().contentHash(), result.applicationHash());
        assertEquals(receipt.evidence().repaidAt(), result.appliedAt());
    }

    @Test
    void shouldRejectLoanRepaymentCaseWithoutParameters() {
        loanRepaymentContext(MaintenanceWorkflowTaskStatus.READY);

        MaintenanceValidationException error = assertValidationFailure(() -> service.apply(input()).join());

        assertTrue(error.getMessage().contains("loanRepayment"), error.getMessage());
        verify(loanRepaymentOrchestrator, never()).repay(any());
        verify(policyApplicationPort, never()).apply(any());
        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldRejectLoanRepaymentItemMixedWithOtherItems() {
        loanRepaymentContext(MaintenanceWorkflowTaskStatus.READY);
        MaintenanceWorkflowTaskView other = effectTask("effect-task-2", "POLICY_INFO_CHANGE", 1);
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(effectTask("effect-task-1", "LOAN_REPAYMENT", 0), other));
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(effectTask("effect-task-1", "LOAN_REPAYMENT", 0)));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.apply(loanRepaymentInput()).join());

        assertTrue(error.getMessage().contains("loanRepayment"), error.getMessage());
        verify(loanRepaymentOrchestrator, never()).repay(any());
    }

    @Test
    void shouldRejectLoanRepaymentCaseOnScheduledEntry() {
        loanRepaymentContext(MaintenanceWorkflowTaskStatus.READY);
        MaintenanceView scheduled = schedulableSwitchCase();
        scheduled.setProductId("product-1");
        scheduled.setCustomerId("customer-1");
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(scheduled));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.applyScheduled(loanRepaymentInput(),
                        LocalDateTime.parse("2026-09-01T00:00:00")).join());
        assertTrue(error.getMessage().contains("保单贷款还款"), error.getMessage());

        verify(loanRepaymentOrchestrator, never()).repay(any());
    }

    @Test
    void shouldRejectLoanRepaymentRetryWhenFrozenRequestPayloadDiffers() {
        MaintenanceWorkflowTaskView waiting = loanRepaymentContext(
                MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL);
        waiting.setEffectRequestId("effect-case-1");
        // 已冻结请求的哈希是「首次目标明细 + 还款额」的摘要；重试改用其它摘要即载荷漂移
        waiting.setEffectRequestHash("b".repeat(64));

        MaintenanceValidationException error = assertValidationFailure(
                () -> service.apply(loanRepaymentInput()).join());

        assertTrue(error.getMessage().contains("loanRepayment"), error.getMessage());
        verify(loanRepaymentOrchestrator, never()).repay(any());
    }

    @Test
    void shouldReplayLoanRepaymentReceiptForCompletedLoanRepaymentCase() {
        MaintenanceWorkflowTaskView completed = loanRepaymentContext(
                MaintenanceWorkflowTaskStatus.COMPLETED);
        completed.setEffectRequestId("effect-case-1");
        completed.setEffectRequestHash("a".repeat(64));
        completed.setEffectExpectedPolicyVersion(7L);
        completed.setEffectTimeType(EffectiveTimeType.IMMEDIATE);
        completed.setEffectRequestedEffectiveAt(LocalDateTime.parse("2026-08-25T09:59:59"));
        completed.setEffectProposedSnapshotHash("d".repeat(64));
        completed.setLoanRepaymentId("RPAY" + "a".repeat(28));
        completed.setLastEvidenceHash("f".repeat(64));
        completed.setLoanRepaidAt(LocalDateTime.parse("2026-08-25T10:00:00"));

        MaintenanceEffectApplicationResult result = service.apply(loanRepaymentInput()).join();

        assertEquals("effect-case-1", result.requestId());
        assertNull(result.endorsementNo());
        assertEquals("f".repeat(64), result.applicationHash());
        assertEquals(LocalDateTime.parse("2026-08-25T10:00:00"), result.appliedAt());
        verifyNoInteractions(loanRepaymentOrchestrator);
    }

    // ==================== 保单补发（POLICY_REISSUE）生效出口 ====================

    @Test
    void shouldRoutePolicyReissueCaseToDocumentPortWithoutPolicyApplication() {
        policyReissueContext(MaintenanceWorkflowTaskStatus.READY);
        when(policyReissueDocumentPort.requestPolicyReissue(any()))
                .thenAnswer(invocation -> ((PolicyReissueDocument) invocation.getArgument(0)).reissueRequestId());

        MaintenanceEffectApplicationResult result = service.apply(policyReissueInput()).join();

        verify(policyApplicationPort, never()).apply(any());
        verify(investmentAccountSwitchPort, never()).switchByPolicy(any());
        verify(loanDisbursementOrchestrator, never()).disburse(any());
        verify(loanRepaymentOrchestrator, never()).repay(any());

        ArgumentCaptor<PolicyReissueDocument> documentCaptor =
                ArgumentCaptor.forClass(PolicyReissueDocument.class);
        verify(policyReissueDocumentPort).requestPolicyReissue(documentCaptor.capture());
        PolicyReissueDocument document = documentCaptor.getValue();
        assertEquals("maintenance-1", document.maintenanceId());
        assertEquals("policy-1", document.policyId(), "补发指向保单自身凭证，要素取自案件保单");
        assertEquals("POLICY_REISSUE", document.maintenanceTypeCode());
        assertEquals(PolicyReissueDocument.deriveReissueRequestId("tenant-1", "maintenance-1"),
                document.reissueRequestId(),
                "请求号稳定派生——重试与重放收敛到同一号，对端据以去重");

        List<Object> commands = capturedCommands(2);
        RequestMaintenanceCaseEffectCommand frozen = (RequestMaintenanceCaseEffectCommand) commands.get(0);
        RecordMaintenanceCasePolicyReissueCommand receipt =
                (RecordMaintenanceCasePolicyReissueCommand) commands.get(1);
        assertEquals("tenant-1", frozen.tenantId());
        assertEquals(document.reissueRequestId(), receipt.evidence().reissueRequestId(),
                "回执记录的请求号必须与交付对端的号逐字一致");
        assertNull(result.endorsementNo(), "补发不改保单，无批单号");
        assertEquals(receipt.evidence().contentHash(), result.applicationHash());
        assertEquals(receipt.evidence().requestedAt(), result.appliedAt());
    }

    @Test
    void shouldFailClosedWhenPolicyReissueDeliveryFails() {
        policyReissueContext(MaintenanceWorkflowTaskStatus.READY);
        when(policyReissueDocumentPort.requestPolicyReissue(any()))
                .thenThrow(new IllegalStateException("document topic unreachable"));

        assertThrows(java.util.concurrent.CompletionException.class,
                () -> service.apply(policyReissueInput()).join());

        FailMaintenanceCaseEffectCommand failure =
                (FailMaintenanceCaseEffectCommand) capturedCommands(2).get(1);
        assertEquals("POLICY_REISSUE_FAILED", failure.failureCode(),
                "补发请求未交付即案件生效失败——请求事实是生效的实质内容，不得静默跳过");
        verify(commandGateway, never()).send(any(RecordMaintenanceCasePolicyReissueCommand.class));
    }

    @Test
    void shouldRejectPolicyReissueItemMixedWithOtherItems() {
        policyReissueContext(MaintenanceWorkflowTaskStatus.READY);
        MaintenanceWorkflowTaskView other = effectTask("effect-task-2", "POLICY_INFO_CHANGE", 1);
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(effectTask("effect-task-1", "POLICY_REISSUE", 0), other));
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(effectTask("effect-task-1", "POLICY_REISSUE", 0)));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.apply(policyReissueInput()).join());

        assertTrue(error.getMessage().contains("policyReissue"), error.getMessage());
        verify(policyReissueDocumentPort, never()).requestPolicyReissue(any());
    }

    @Test
    void shouldRejectPolicyReissueCaseOnScheduledEntry() {
        policyReissueContext(MaintenanceWorkflowTaskStatus.READY);
        MaintenanceView scheduled = schedulableSwitchCase();
        scheduled.setProductId("product-1");
        scheduled.setCustomerId("customer-1");
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(scheduled));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.applyScheduled(policyReissueInput(),
                        LocalDateTime.parse("2026-09-01T00:00:00")).join());

        assertTrue(error.getMessage().contains("保单补发"), error.getMessage());
        verify(policyReissueDocumentPort, never()).requestPolicyReissue(any());
    }

    @Test
    void shouldRejectPolicyReissueRetryWhenFrozenRequestPayloadDiffers() {
        MaintenanceWorkflowTaskView waiting =
                policyReissueContext(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL);
        waiting.setEffectRequestId("effect-case-1");
        // 补发载荷由案件事实唯一决定，故冻结摘要恒定；此处换成别的摘要即「重试换了载荷」
        waiting.setEffectRequestHash("b".repeat(64));

        MaintenanceValidationException error = assertValidationFailure(
                () -> service.apply(policyReissueInput()).join());

        assertTrue(error.getMessage().contains("policyReissue"), error.getMessage());
        verify(policyReissueDocumentPort, never()).requestPolicyReissue(any());
    }

    @Test
    void shouldReplayPolicyReissueReceiptForCompletedCase() {
        MaintenanceWorkflowTaskView completed =
                policyReissueContext(MaintenanceWorkflowTaskStatus.COMPLETED);
        completed.setEffectRequestId("effect-case-1");
        completed.setEffectRequestHash("a".repeat(64));
        completed.setEffectExpectedPolicyVersion(7L);
        completed.setEffectTimeType(EffectiveTimeType.IMMEDIATE);
        completed.setEffectRequestedEffectiveAt(LocalDateTime.parse("2026-08-25T09:59:59"));
        completed.setEffectProposedSnapshotHash("d".repeat(64));
        completed.setReissueRequestId("RISS" + "a".repeat(28));
        completed.setLastEvidenceHash("f".repeat(64));
        completed.setReissueRequestedAt(LocalDateTime.parse("2026-08-25T10:00:00"));

        MaintenanceEffectApplicationResult result = service.apply(policyReissueInput()).join();

        assertEquals("effect-case-1", result.requestId());
        assertNull(result.endorsementNo());
        assertEquals("f".repeat(64), result.applicationHash());
        assertEquals(LocalDateTime.parse("2026-08-25T10:00:00"), result.appliedAt());
        verifyNoInteractions(policyReissueDocumentPort);
    }

    private MaintenanceEffectApplicationInput policyReissueInput() {
        return new MaintenanceEffectApplicationInput(
                "maintenance-1", "effect-task-1", "operation-1", "operator-1", "tenant-1",
                MaintenanceChannel.API);
    }

    private MaintenanceWorkflowTaskView policyReissueContext(MaintenanceWorkflowTaskStatus status) {
        MaintenanceView caseView = visibleContext(true);
        caseView.setProductId("product-1");
        caseView.setCustomerId("customer-1");
        MaintenanceWorkflowTaskView task = effectTask("effect-task-1", "POLICY_REISSUE", 0);
        task.setStatus(status);
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(task));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(task));
        when(fieldRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of());
        return task;
    }

    private MaintenanceWorkflowTaskView loanRepaymentContext(MaintenanceWorkflowTaskStatus status) {
        MaintenanceView caseView = visibleContext(true);
        caseView.setProductId("product-1");
        caseView.setCustomerId("customer-1");
        MaintenanceWorkflowTaskView task = effectTask("effect-task-1", "LOAN_REPAYMENT", 0);
        task.setStatus(status);
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(task));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(task));
        when(fieldRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of());
        return task;
    }

    private LoanRepaymentFact loanRepaymentFact() {
        return LoanRepaymentFact.of(new PolicyLoanRepaymentReceipt(
                        "RPAY" + "a".repeat(28), "policy-1", "LOAN001", new BigDecimal("911.10"),
                        new BigDecimal("11.10"), new BigDecimal("900.00"), new BigDecimal("11.10"),
                        BigDecimal.ZERO, BigDecimal.ZERO, true, LocalDateTime.parse("2026-09-28T12:00:00")),
                "POSTING-9", "RPAY" + "a".repeat(28));
    }

    private MaintenanceEffectApplicationInput loanRepaymentInput() {
        return new MaintenanceEffectApplicationInput(
                "maintenance-1", "effect-task-1", "operation-1", "operator-1", "tenant-1",
                MaintenanceChannel.API, null, null,
                new MaintenanceLoanRepaymentInput("LOAN001", new BigDecimal("911.10")));
    }

    private MaintenanceWorkflowTaskView loanContext(MaintenanceWorkflowTaskStatus status) {
        MaintenanceView caseView = visibleContext(true);
        caseView.setProductId("product-1");
        caseView.setCustomerId("customer-1");
        MaintenanceWorkflowTaskView task = effectTask("effect-task-1", "POLICY_LOAN", 0);
        task.setStatus(status);
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(task));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(task));
        when(fieldRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of());
        return task;
    }

    private LoanDisbursementFact loanFact() {
        return new LoanDisbursementFact(
                "LOAN" + "a".repeat(28), "policy-1", new BigDecimal("5000"),
                new BigDecimal("0.0500"), LocalDateTime.parse("2026-09-28T12:00:00"),
                "POSTING-9", "PAYOUT-7");
    }

    private MaintenanceEffectApplicationInput loanInput() {
        return new MaintenanceEffectApplicationInput(
                "maintenance-1", "effect-task-1", "operation-1", "operator-1", "tenant-1",
                MaintenanceChannel.API, null, new MaintenanceLoanDisbursementInput(new BigDecimal("5000")));
    }

    /**
     * 减额缴清案件上下文：生效任务只有 REDUCED_PAID_UP 一项，且<b>零字段提案</b>
     * （减额缴清结构上无提案字段，新保额由核定产出，故必须覆盖 setUp 的默认字段桩）。
     */
    private MaintenanceView reducedPaidUpContext(MaintenanceWorkflowTaskStatus status) {
        MaintenanceView caseView = visibleContext(true);
        caseView.setProductId("product-1");
        MaintenanceWorkflowTaskView task = effectTask("effect-task-1", "REDUCED_PAID_UP", 0);
        task.setStatus(status);
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(task));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(task));
        when(fieldRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of());
        return caseView;
    }

    /** 现金价值 11000.00 ÷ 趸缴净费率 1.0000 = 新保额 11000.00（≤ 原保额 20000.00，减额成立）。 */
    private ReducedPaidUpFact reducedPaidUpFact() {
        return new ReducedPaidUpFact(
                "policy-1", "PP-MAIN", new BigDecimal("11000.00"), new BigDecimal("20000.00"),
                new BigDecimal("1.0000"), new BigDecimal("11000.00"));
    }

    private MaintenanceWorkflowTaskView investmentSwitchContext(MaintenanceWorkflowTaskStatus status) {
        MaintenanceView caseView = visibleContext(true);
        MaintenanceWorkflowTaskView task = effectTask("effect-task-1", "FUND_SWITCH", 0);
        task.setStatus(status);
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1"))
                .thenReturn(Optional.of(task));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(task));
        when(fieldRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of());
        return task;
    }

    private MaintenanceView schedulableSwitchCase() {
        MaintenanceView caseView = new MaintenanceView();
        caseView.setMaintenanceId("maintenance-1");
        caseView.setPolicyId("policy-1");
        caseView.setTenantId("tenant-1");
        caseView.setIndependentCase(true);
        caseView.setInitializationCompleted(true);
        caseView.setEffectiveTimeType(EffectiveTimeType.FUTURE);
        return caseView;
    }

    private InvestmentAccountSwitchPort.InvestmentSwitchFact switchFact() {
        return new InvestmentAccountSwitchPort.InvestmentSwitchFact("account-1",
                new BigDecimal("1.25000000"), new BigDecimal("800.00000000"),
                new BigDecimal("1000.00000000"), "CNY");
    }

    private MaintenanceEffectApplicationInput switchInput() {
        return new MaintenanceEffectApplicationInput(
                "maintenance-1", "effect-task-1", "operation-1", "operator-1", "tenant-1",
                MaintenanceChannel.API, new MaintenanceInvestmentSwitchInput(
                        new BigDecimal("100.00000000"), new BigDecimal("1.25000000"), "CNY", "FUND-B"));
    }

    /** 断言行内校验失败：校验发生在异步编排链内，join 后由 {@code CompletionException} 包装 */
    // ==================== 金额型保全（追加/减额缴费、追加投资、部分领取）生效出口 ====================

    /**
     * 金额型案件无字段、无状态动作，其变更实质是一笔<b>已登记</b>的收付差额——
     * 必须随请求递到保单域（批单财务事实），且差额取自已入账事实而非调用方自报。
     * <p>
     * 逐个覆盖 AC-07 中走本域金额通道的 4 类保全项（追加/减额缴费、追加投资、部分领取）：
     * 生效载荷按保全项编码取自目录，漏登记任一项即回落字段通道、差额随请求丢失。
     * </p>
     */
    @ParameterizedTest
    @ValueSource(strings = {"ADDITIONAL_PAYMENT", "REDUCTION_PAYMENT", "TOP_UP", "PARTIAL_WITHDRAWAL"})
    void shouldSubmitRecordedPostingDifferenceAsAmountFact(String itemCode) {
        amountContext(itemCode, MaintenanceWorkflowTaskStatus.READY, true);
        ArgumentCaptor<PolicyMaintenanceApplicationPort.ApplicationRequest> requestCaptor =
                ArgumentCaptor.forClass(PolicyMaintenanceApplicationPort.ApplicationRequest.class);
        when(policyApplicationPort.apply(any())).thenAnswer(invocation -> {
            PolicyMaintenanceApplicationPort.ApplicationRequest request = invocation.getArgument(0);
            assertEquals(List.of(), request.changes(), "金额型案件不带字段变更");
            assertEquals(PolicyMaintenanceAction.NONE, request.stateAction(), "金额型案件不改合同状态");
            assertNotNull(request.amountEffect(), "金额型案件必须携带收付差额事实，否则保单域按空交易拒绝");
            assertEquals(itemCode, request.amountEffect().itemCode());
            assertEquals(0, request.amountEffect().amount().compareTo(new BigDecimal("1000.00")));
            assertEquals(MaintenanceBalanceDirection.DEBIT, request.amountEffect().direction());
            assertEquals("CNY", request.amountEffect().currency());
            assertEquals("POSTING-1", request.amountEffect().sourceRef());
            return successfulPolicyFact(request);
        });

        MaintenanceEffectApplicationResult result = service.apply(input()).join();

        verify(policyApplicationPort).apply(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().changeSummary().contains(";amount=DEBIT:1000.00:CNY"),
                "批单摘要须含差额，否则事后无从对账: " + requestCaptor.getValue().changeSummary());
        assertEquals("END-RETROACTIVE", result.endorsementNo());
        List<Object> commands = capturedCommands(2);
        assertInstanceOf(RequestMaintenanceCaseEffectCommand.class, commands.get(0));
        assertInstanceOf(RecordMaintenanceCasePolicyApplicationCommand.class, commands.get(1));
    }

    /** 取不到已登记差额即空交易：金额型案件的变更实质就是这笔差额，放行等于写入一条「什么都没发生」的批单。 */
    @Test
    void shouldRejectAmountCaseWithoutRecordedDifference() {
        amountContext("ADDITIONAL_PAYMENT", MaintenanceWorkflowTaskStatus.READY, false);

        // 闸口在派发任何命令之前同步拒绝（非异步链路内的失败）
        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.apply(input()).join());

        assertTrue(error.getMessage().contains("收付差额"), error.getMessage());
        verify(policyApplicationPort, never()).apply(any());
        verify(commandGateway, never()).send(any());
    }

    /** 金额型项与其它生效项混案：一笔差额只能对应一次合同变更，混案后归因无从确定。 */
    @Test
    void shouldRejectAmountItemMixedWithOtherItems() {
        amountContext("ADDITIONAL_PAYMENT", MaintenanceWorkflowTaskStatus.READY, true);
        MaintenanceWorkflowTaskView other = effectTask("effect-task-2", "POLICY_INFO_CHANGE", 1);
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenReturn(List.of(effectTask("effect-task-1", "ADDITIONAL_PAYMENT", 0), other));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> service.apply(input()).join());

        assertTrue(error.getMessage().contains("金额型保全项不得与其它生效项混合"), error.getMessage());
        verify(policyApplicationPort, never()).apply(any());
    }

    private MaintenanceView amountContext(
            String itemCode,
            MaintenanceWorkflowTaskStatus status,
            boolean withRecordedDifference) {
        MaintenanceView caseView = visibleContext(true);
        MaintenanceWorkflowTaskView effect = effectTask("effect-task-1", itemCode, 0);
        effect.setStatus(status);
        MaintenanceWorkflowTaskView fee = effectTask("fee-task-1", itemCode, 0);
        fee.setStepType(MaintenanceStepType.FEE_SETTLEMENT);
        fee.setStatus(MaintenanceWorkflowTaskStatus.COMPLETED);
        if (withRecordedDifference) {
            fee.setBillingPostingId("POSTING-1");
            fee.setBillingPostingStatus(MaintenanceBillingPostingStatus.POSTED);
            fee.setBillingPostingDirection(MaintenanceBalanceDirection.DEBIT);
            fee.setBillingPostingAmount(new BigDecimal("1000.00"));
            fee.setBillingPostingCurrency("CNY");
        }
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "maintenance-1", "effect-task-1")).thenReturn(Optional.of(effect));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1")).thenReturn(List.of(effect, fee));
        when(fieldRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                "tenant-1", "maintenance-1")).thenReturn(List.of());
        return caseView;
    }

    private MaintenanceValidationException assertValidationFailure(Runnable action) {
        java.util.concurrent.CompletionException wrapper = assertThrows(
                java.util.concurrent.CompletionException.class, action::run);
        assertTrue(wrapper.getCause() instanceof MaintenanceValidationException,
                String.valueOf(wrapper.getCause()));
        return (MaintenanceValidationException) wrapper.getCause();
    }

    private List<Object> capturedCommands(int expected) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, times(expected)).send(captor.capture());
        return captor.getAllValues();
    }

    private MaintenanceWorkflowTaskView effectTask(String taskId, String itemCode, int itemOrder) {
        MaintenanceWorkflowTaskView task = new MaintenanceWorkflowTaskView();
        task.setTaskId(taskId);
        task.setMaintenanceId("maintenance-1");
        task.setItemCode(itemCode);
        task.setItemOrder(itemOrder);
        task.setStepType(MaintenanceStepType.EFFECT);
        task.setMode(MaintenanceStepMode.REQUIRED);
        task.setStatus(MaintenanceWorkflowTaskStatus.READY);
        task.setRetryCount(0);
        return task;
    }

    private MaintenanceWorkflowTaskView completedEffectTask(
            String taskId,
            String endorsementNo,
            String applicationHash) {
        MaintenanceWorkflowTaskView task = effectTask(taskId, "POLICY_INFO_CHANGE", 0);
        task.setStatus(MaintenanceWorkflowTaskStatus.COMPLETED);
        task.setEffectRequestId("effect-case-1");
        task.setEffectRequestHash("a".repeat(64));
        task.setEffectExpectedPolicyVersion(7L);
        task.setEffectTimeType(EffectiveTimeType.IMMEDIATE);
        task.setEffectRequestedEffectiveAt(LocalDateTime.parse("2026-08-25T09:59:59"));
        task.setEffectProposedSnapshotHash("d".repeat(64));
        task.setPolicyEndorsementNo(endorsementNo);
        task.setPolicyActualVersion(8L);
        task.setPolicyApplicationHash(applicationHash);
        task.setPolicyStateAction(PolicyMaintenanceAction.NONE);
        task.setAppliedSnapshotStorageKey("axon-event://policy/tenant-1/policy-1?version=8");
        task.setAppliedSnapshotHash("e".repeat(64));
        task.setAppliedSnapshotPolicyVersion(8L);
        task.setAppliedSnapshotCapturedAt("2026-08-25T02:00:00Z");
        task.setAppliedFieldsJson("[]");
        task.setPolicyAppliedAt(LocalDateTime.parse("2026-08-25T10:00:00"));
        return task;
    }

    private AppliedSnapshot appliedSnapshot() {
        return new AppliedSnapshot(
                "axon-event://policy/tenant-1/policy-1?version=8", "c".repeat(64), 8,
                OffsetDateTime.parse("2026-08-25T10:00:00+08:00"));
    }

    private MaintenanceEffectApplicationInput input() {
        return new MaintenanceEffectApplicationInput(
                "maintenance-1", "effect-task-1", "operation-1",
                "operator-1", "tenant-1", MaintenanceChannel.API);
    }
}
