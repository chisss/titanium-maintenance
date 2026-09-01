package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.effect.MaintenanceEffectApplicationInput;
import com.titanium.maintenance.application.model.field.MaintenanceFieldConflictOperationResult;
import com.titanium.maintenance.command.FailMaintenanceCaseEffectCommand;
import com.titanium.maintenance.command.RecordMaintenanceCasePolicyApplicationCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectCompensationCommand;
import com.titanium.maintenance.command.RequestMaintenanceCaseEffectCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort.ResolutionFact;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort.ResolutionLineFact;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCapabilityEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.ApplicationFact;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.AppliedField;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.AppliedSnapshot;
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
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

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
        service = new MaintenanceEffectApplicationService(
                commandGateway, maintenanceViewRepository, taskRepository, fieldRepository,
                snapshotRepository, periodAdjustmentRepository, fieldConflictApplicationService, fieldCatalogPort,
                policyApplicationPort, billingResolutionPort);
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
        verify(billingResolutionPort, never()).get(any(), any());
        assertEquals(MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED,
                caseView.getRetroactivePeriodRecalculationStatus());
    }

    @Test
    void shouldReconcileClosedPeriodResolutionBeforeRetroactivePolicyCall() {
        retroactiveContext(true);
        when(billingResolutionPort.get("tenant-1", "billing-batch-1"))
                .thenReturn(resolutionFact());
        when(policyApplicationPort.apply(any())).thenAnswer(invocation ->
                successfulPolicyFact(invocation.getArgument(0)));

        service.apply(input()).join();

        verify(billingResolutionPort).get("tenant-1", "billing-batch-1");
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
                capability, PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE, null);
        when(fieldCatalogPort.getCatalog(any())).thenReturn(new PolicyFieldCatalogEvidence(
                "tenant-1", null, null, LocalDate.now(), "2026.08.25.1", "d".repeat(64),
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
