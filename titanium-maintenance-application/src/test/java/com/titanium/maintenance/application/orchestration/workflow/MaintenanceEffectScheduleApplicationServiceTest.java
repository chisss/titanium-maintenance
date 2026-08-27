package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.MaintenanceEffectScheduleOperationInput;
import com.titanium.maintenance.application.configuration.MaintenanceEffectSchedulingProperties;
import com.titanium.maintenance.application.model.MaintenanceEffectApplicationResult;
import com.titanium.maintenance.command.CompleteMaintenanceEffectScheduleCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectScheduleAttemptCommand;
import com.titanium.maintenance.command.RecordMaintenanceEffectScheduleFailureCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort;
import com.titanium.maintenance.port.MaintenanceEffectScheduleLeasePort;
import com.titanium.maintenance.port.MaintenanceEffectScheduleLeasePort.ScheduleLease;
import com.titanium.maintenance.port.PaymentPremiumCollectionPort;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.TenantTimeZonePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

class MaintenanceEffectScheduleApplicationServiceTest {

    private static final LocalDateTime EXECUTION_AT = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
    private static final MaintenanceEffectApplicationResult APPLIED_RESULT =
            new MaintenanceEffectApplicationResult(
                    "request-1", "END-001", 8L, "b".repeat(64),
                    LocalDateTime.parse("2026-08-26T10:00:00"));

    private CommandGateway commandGateway;
    private MaintenanceViewRepository maintenanceViewRepository;
    private MaintenanceWorkflowTaskViewRepository taskRepository;
    private PolicyMaintenanceSnapshotPort policySnapshotPort;
    private MaintenanceEffectApplicationService effectApplicationService;
    private MaintenanceEffectScheduleLeasePort leasePort;
    private MaintenanceEffectScheduleApplicationService service;
    private MaintenanceView caseView;
    private MaintenanceWorkflowTaskView effectTask;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        maintenanceViewRepository = mock(MaintenanceViewRepository.class);
        taskRepository = mock(MaintenanceWorkflowTaskViewRepository.class);
        policySnapshotPort = mock(PolicyMaintenanceSnapshotPort.class);
        effectApplicationService = mock(MaintenanceEffectApplicationService.class);
        leasePort = mock(MaintenanceEffectScheduleLeasePort.class);
        MaintenanceEffectSchedulingProperties properties = new MaintenanceEffectSchedulingProperties();
        service = new MaintenanceEffectScheduleApplicationService(
                commandGateway, maintenanceViewRepository, taskRepository, policySnapshotPort,
                mock(BillingPremiumLifecyclePort.class), mock(PaymentPremiumCollectionPort.class),
                effectApplicationService, leasePort, mock(TenantTimeZonePort.class), properties);

        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        caseView = caseView();
        effectTask = effectTask(MaintenanceWorkflowTaskStatus.READY);
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(caseView));
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "maintenance-1"))
                .thenAnswer(ignored -> List.of(effectTask));
        when(policySnapshotPort.capture(any())).thenReturn(snapshot(7L));
        when(effectApplicationService.applyScheduled(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(APPLIED_RESULT));
        when(leasePort.acquireNow(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(lease(0)));
    }

    @Test
    void shouldExecuteDueScheduleAndClosePlan() {
        MaintenanceEffectApplicationResult result = service.executeNow(input());

        assertEquals(APPLIED_RESULT, result);
        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).send(commands.capture());
        assertEquals(RecordMaintenanceEffectScheduleAttemptCommand.class,
                commands.getAllValues().get(0).getClass());
        assertEquals(CompleteMaintenanceEffectScheduleCommand.class,
                commands.getAllValues().get(1).getClass());
        verify(leasePort).release(org.mockito.ArgumentMatchers.eq("maintenance-1"), any());
    }

    @Test
    void shouldRejectManualExecutionBeforeScheduleIsDue() {
        caseView.setEffectScheduleNextExecutionAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));

        assertThrows(MaintenanceValidationException.class, () -> service.executeNow(input()));

        verify(leasePort, never()).acquireNow(any(), any(), any(), any(), any());
        verify(commandGateway, never()).send(any());
        verify(effectApplicationService, never()).applyScheduled(any(), any());
    }

    @Test
    void shouldTerminateScheduleWhenPolicyVersionChanged() {
        when(policySnapshotPort.capture(any())).thenReturn(snapshot(8L));

        assertThrows(MaintenanceValidationException.class, () -> service.executeNow(input()));

        RecordMaintenanceEffectScheduleFailureCommand failure = capturedFailure();
        assertEquals("MaintenanceValidationException", failure.errorCode());
        assertEquals(true, failure.terminal());
        assertEquals(null, failure.retryAt());
        verify(effectApplicationService, never()).applyScheduled(any(), any());
    }

    @Test
    void shouldRetryWhenWorkflowIsNotReadyAtDueTime() {
        effectTask.setStatus(MaintenanceWorkflowTaskStatus.PENDING);

        assertThrows(MaintenanceEffectSchedulePendingException.class, () -> service.executeNow(input()));

        RecordMaintenanceEffectScheduleFailureCommand failure = capturedFailure();
        assertFalse(failure.terminal());
        assertNotNull(failure.retryAt());
        assertEquals("MaintenanceEffectSchedulePendingException", failure.errorCode());
        verify(effectApplicationService, never()).applyScheduled(any(), any());
    }

    @Test
    void shouldNotRecordFailureWhenAttemptFactCannotBeWritten() {
        when(commandGateway.send(any())).thenAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof RecordMaintenanceEffectScheduleAttemptCommand) {
                return CompletableFuture.failedFuture(new IllegalStateException("event store unavailable"));
            }
            return CompletableFuture.completedFuture(null);
        });

        assertThrows(IllegalStateException.class, () -> service.executeNow(input()));

        verify(commandGateway, org.mockito.Mockito.times(1)).send(any());
        verify(effectApplicationService, never()).applyScheduled(any(), any());
        verify(leasePort).release(org.mockito.ArgumentMatchers.eq("maintenance-1"), any());
    }

    @Test
    void shouldNotRegressAppliedCaseWhenScheduleCloseFails() {
        when(commandGateway.send(any())).thenAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof CompleteMaintenanceEffectScheduleCommand) {
                return CompletableFuture.failedFuture(new IllegalStateException("event store unavailable"));
            }
            return CompletableFuture.completedFuture(null);
        });

        assertThrows(IllegalStateException.class, () -> service.executeNow(input()));

        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).send(commands.capture());
        assertFalse(commands.getAllValues().stream()
                .anyMatch(RecordMaintenanceEffectScheduleFailureCommand.class::isInstance));
    }

    @Test
    void shouldCloseAlreadyAppliedScheduleWithoutCallingPolicyAgain() {
        caseView.setEffectStatus(MaintenanceEffectStatus.APPLIED);
        caseView.setEffectScheduleLastAttemptId("schedule-1:attempt:1");
        effectTask.setStatus(MaintenanceWorkflowTaskStatus.COMPLETED);
        effectTask.setEffectRequestId(APPLIED_RESULT.requestId());
        effectTask.setPolicyEndorsementNo(APPLIED_RESULT.endorsementNo());
        effectTask.setPolicyActualVersion(APPLIED_RESULT.actualPolicyVersion());
        effectTask.setPolicyApplicationHash(APPLIED_RESULT.applicationHash());
        effectTask.setPolicyAppliedAt(APPLIED_RESULT.appliedAt());

        MaintenanceEffectApplicationResult result = service.executeNow(input());

        assertEquals(APPLIED_RESULT, result);
        ArgumentCaptor<Object> command = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway).send(command.capture());
        CompleteMaintenanceEffectScheduleCommand completed =
                (CompleteMaintenanceEffectScheduleCommand) command.getValue();
        assertEquals("schedule-1:attempt:1", completed.attemptId());
        verify(policySnapshotPort, never()).capture(any());
        verify(effectApplicationService, never()).applyScheduled(any(), any());
    }

    @Test
    void shouldRecoverCompletedScheduleWithoutAcquiringLease() {
        caseView.setEffectStatus(MaintenanceEffectStatus.APPLIED);
        caseView.setEffectScheduleStatus(MaintenanceEffectScheduleStatus.COMPLETED);
        effectTask.setStatus(MaintenanceWorkflowTaskStatus.COMPLETED);
        effectTask.setEffectRequestId(APPLIED_RESULT.requestId());
        effectTask.setPolicyEndorsementNo(APPLIED_RESULT.endorsementNo());
        effectTask.setPolicyActualVersion(APPLIED_RESULT.actualPolicyVersion());
        effectTask.setPolicyApplicationHash(APPLIED_RESULT.applicationHash());
        effectTask.setPolicyAppliedAt(APPLIED_RESULT.appliedAt());

        MaintenanceEffectApplicationResult result = service.executeNow(input());

        assertEquals(APPLIED_RESULT, result);
        verify(leasePort, never()).acquireNow(any(), any(), any(), any(), any());
        verify(commandGateway, never()).send(any());
        verify(effectApplicationService, never()).applyScheduled(any(), any());
    }

    private RecordMaintenanceEffectScheduleFailureCommand capturedFailure() {
        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).send(commands.capture());
        return (RecordMaintenanceEffectScheduleFailureCommand) commands.getAllValues().get(1);
    }

    private MaintenanceEffectScheduleOperationInput input() {
        return new MaintenanceEffectScheduleOperationInput(
                "maintenance-1", "operation-1", "立即执行", "operator-1",
                "tenant-1", MaintenanceChannel.MANUAL);
    }

    private ScheduleLease lease(int attemptCount) {
        return new ScheduleLease(
                "maintenance-1", "tenant-1", "schedule-1", "effect-task-1",
                EXECUTION_AT, "Asia/Shanghai", attemptCount);
    }

    private MaintenanceView caseView() {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("maintenance-1");
        view.setTenantId("tenant-1");
        view.setPolicyId("policy-1");
        view.setCustomerId("customer-1");
        view.setIndependentCase(true);
        view.setInitializationCompleted(true);
        view.setPolicyBaselineVersion(7L);
        view.setEffectiveTimeType(EffectiveTimeType.FUTURE);
        view.setEffectStatus(MaintenanceEffectStatus.SCHEDULED);
        view.setEffectScheduleId("schedule-1");
        view.setEffectScheduleStatus(MaintenanceEffectScheduleStatus.ACTIVE);
        view.setEffectScheduleTenantZoneId("Asia/Shanghai");
        view.setEffectScheduleNextExecutionAt(EXECUTION_AT);
        return view;
    }

    private MaintenanceWorkflowTaskView effectTask(MaintenanceWorkflowTaskStatus status) {
        MaintenanceWorkflowTaskView task = new MaintenanceWorkflowTaskView();
        task.setTaskId("effect-task-1");
        task.setMaintenanceId("maintenance-1");
        task.setTenantId("tenant-1");
        task.setStepType(MaintenanceStepType.EFFECT);
        task.setStatus(status);
        return task;
    }

    private PolicyMaintenanceSnapshot snapshot(long policyVersion) {
        OffsetDateTime capturedAt = OffsetDateTime.of(
                2026, 8, 25, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P202608250001", CustomerId.of("customer-1"),
                "product-1", "product-v1", "plan-v1", PolicyStatus.EFFECTIVE, policyVersion,
                capturedAt, new MaintenanceSnapshotReference(
                        "policy/policy-1/versions/" + policyVersion, "a".repeat(64), policyVersion, capturedAt),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13800000000")));
    }
}
