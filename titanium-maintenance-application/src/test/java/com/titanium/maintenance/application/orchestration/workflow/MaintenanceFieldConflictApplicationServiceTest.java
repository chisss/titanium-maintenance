package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.RefreshMaintenanceFieldConflictsInput;
import com.titanium.maintenance.application.command.ResolveMaintenanceFieldConflictInput;
import com.titanium.maintenance.command.RefreshMaintenanceFieldConflictsCommand;
import com.titanium.maintenance.command.ResolveMaintenanceFieldConflictCommand;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.port.PolicyMaintenanceApplicationPort;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

class MaintenanceFieldConflictApplicationServiceTest {

    private PolicyMaintenanceSnapshotPort snapshotPort;
    private MaintenanceViewRepository viewRepository;
    private CommandGateway commandGateway;
    private MaintenanceFieldConflictApplicationService service;

    @BeforeEach
    void setUp() {
        snapshotPort = mock(PolicyMaintenanceSnapshotPort.class);
        viewRepository = mock(MaintenanceViewRepository.class);
        commandGateway = mock(CommandGateway.class);
        service = new MaintenanceFieldConflictApplicationService(snapshotPort, viewRepository, commandGateway);
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("case-1");
        view.setPolicyId("policy-1");
        view.setTenantId("tenant-1");
        when(viewRepository.findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                "case-1", "tenant-1")).thenReturn(Optional.of(view));
    }

    @Test
    void shouldReadLatestPolicyAndReturnRefreshPlan() {
        PolicyMaintenanceSnapshot snapshot = snapshot();
        MaintenanceFieldConflictPlan plan = conflictPlan();
        when(snapshotPort.capture(any())).thenReturn(snapshot);
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(plan));

        var result = service.refresh(new RefreshMaintenanceFieldConflictsInput(
                "case-1", "refresh-1", "operator-1", "tenant-1")).join();

        assertEquals(8, result.policyVersion());
        assertEquals(1, result.conflictCount());
        ArgumentCaptor<RefreshMaintenanceFieldConflictsCommand> captor =
                ArgumentCaptor.forClass(RefreshMaintenanceFieldConflictsCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals(snapshot, captor.getValue().currentPolicySnapshot());
        assertEquals(64, captor.getValue().requestHash().length());
    }

    @Test
    void shouldUseFixedLengthOperationIdWhenEffectRefreshesMaximumLengthInput() {
        PolicyMaintenanceSnapshot snapshot = snapshot();
        when(snapshotPort.capture(any())).thenReturn(snapshot);
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(conflictPlan()));
        String sourceOperationId = "r".repeat(128);

        service.refreshIfVersionChanged(new RefreshMaintenanceFieldConflictsInput(
                "case-1", sourceOperationId, "operator-1", "tenant-1"), 7).join();

        ArgumentCaptor<RefreshMaintenanceFieldConflictsCommand> captor =
                ArgumentCaptor.forClass(RefreshMaintenanceFieldConflictsCommand.class);
        verify(commandGateway).send(captor.capture());
        String expectedOperationId = PolicyMaintenanceApplicationPort.stageOperationId(
                sourceOperationId, "field-conflict-refresh-policy-v8", 0);
        assertEquals(expectedOperationId, captor.getValue().operationId());
        assertEquals(71, captor.getValue().operationId().length());
    }

    @Test
    void shouldBuildStronglyTypedReentryCommand() {
        MaintenanceFieldChange resolved = conflict().resolveUsingReentered(MaintenanceFieldValue.text("13600000000"));
        MaintenanceFieldConflictPlan plan = plan(resolved, 0, "d".repeat(64));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(plan));

        service.resolve(new ResolveMaintenanceFieldConflictInput(
                "case-1", "resolve-1", "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                MaintenanceFieldConflictResolutionAction.REENTER, PolicyFieldDataType.TEXT, "13600000000",
                "客户确认新号码", "operator-1", "tenant-1")).join();

        ArgumentCaptor<ResolveMaintenanceFieldConflictCommand> captor =
                ArgumentCaptor.forClass(ResolveMaintenanceFieldConflictCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals(PolicyFieldDataType.TEXT, captor.getValue().reenteredValue().dataType());
        assertEquals("13600000000", captor.getValue().reenteredValue().canonicalValue());
        assertEquals(64, captor.getValue().requestHash().length());
    }

    private PolicyMaintenanceSnapshot snapshot() {
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P001", CustomerId.of("customer-1"),
                "product-1", "product-v1", "plan-v1", PolicyStatus.EFFECTIVE, 8,
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
                new MaintenanceSnapshotReference(
                        "policy://policy-1/version/8", "a".repeat(64), 8,
                        OffsetDateTime.parse("2026-08-26T10:00:00+08:00")),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13700000000")));
    }

    private MaintenanceFieldConflictPlan conflictPlan() {
        return plan(conflict(), 1, "c".repeat(64));
    }

    private MaintenanceFieldChange conflict() {
        return MaintenanceFieldChange.propose(
                        "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                        MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"))
                .refreshCurrent(MaintenanceFieldValue.text("13700000000"));
    }

    private MaintenanceFieldConflictPlan plan(
            MaintenanceFieldChange change,
            int conflictCount,
            String contentHash) {
        return new MaintenanceFieldConflictPlan(
                Map.of("POLICY_INFO_CHANGE", List.of(change)),
                Map.of("policy.holder.mobile", change.proposedValue()),
                new MaintenanceSnapshotReference(
                        "maintenance://case-1/proposed", contentHash, 8,
                        OffsetDateTime.parse("2026-08-26T10:00:00+08:00")),
                conflictCount);
    }
}
