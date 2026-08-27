package com.titanium.maintenance.application.orchestration.casecreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.RecordMaintenanceFieldChangesInput.FieldProposalInput;
import com.titanium.maintenance.command.ProposeMaintenanceFieldChangesCommand;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.PolicyFieldCatalogPort;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldCapabilityEvidence;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

class MaintenanceFieldDraftApplicationServiceTest {

    private PolicyMaintenanceSnapshotPort policySnapshotPort;
    private PolicyFieldCatalogPort fieldCatalogPort;
    private CommandGateway commandGateway;
    private MaintenanceViewRepository maintenanceViewRepository;
    private MaintenanceFieldDraftApplicationService service;

    @BeforeEach
    void setUp() {
        policySnapshotPort = mock(PolicyMaintenanceSnapshotPort.class);
        fieldCatalogPort = mock(PolicyFieldCatalogPort.class);
        commandGateway = mock(CommandGateway.class);
        maintenanceViewRepository = mock(MaintenanceViewRepository.class);
        service = new MaintenanceFieldDraftApplicationService(
                policySnapshotPort, fieldCatalogPort, commandGateway, maintenanceViewRepository);
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(caseView()));
    }

    @Test
    void shouldResolveAuthoritiesAndSendStructuredProposalCommand() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.record(request()).join();

        ArgumentCaptor<ProposeMaintenanceFieldChangesCommand> captor = ArgumentCaptor.forClass(
                ProposeMaintenanceFieldChangesCommand.class);
        verify(commandGateway).send(captor.capture());
        ProposeMaintenanceFieldChangesCommand command = captor.getValue();
        assertEquals("maintenance-1", command.id().id());
        assertEquals("13900000000", command.proposals().getFirst().canonicalValue());
        assertEquals(PolicyFieldMaskingPolicy.MOBILE, command.fieldCatalogSnapshot()
                .requireField("policy.holder.mobile").maskingPolicy());
        verify(policySnapshotPort).capture(new PolicyMaintenanceSnapshotRequest("policy-1", "tenant-1"));
    }

    @Test
    void shouldRecordFieldsForSuspendedPolicyAfterOfferingApprovedCaseCreation() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot(PolicyStatus.SUSPENDED));
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.record(request()).join();

        ArgumentCaptor<ProposeMaintenanceFieldChangesCommand> command =
                ArgumentCaptor.forClass(ProposeMaintenanceFieldChangesCommand.class);
        verify(commandGateway).send(command.capture());
        assertEquals(
                PolicyStatus.SUSPENDED,
                command.getValue().currentPolicySnapshot().policyStatus());
    }

    @Test
    void shouldRejectFieldMissingFromCurrentCatalogBeforeSendingCommand() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        MaintenanceFieldDraftRequest invalid = new MaintenanceFieldDraftRequest(
                "maintenance-1", "POLICY_INFO_CHANGE",
                List.of(new FieldProposalInput(
                        null, "policy.unknown", PolicyFieldDataType.TEXT, "value")),
                "operator-1", "tenant-1");

        assertThrows(MaintenanceValidationException.class, () -> service.record(invalid));
    }

    @Test
    void shouldRejectMissingOrUninitializedCaseBeforeReadingPolicy() {
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.empty());

        assertThrows(MaintenanceNotFoundException.class, () -> service.record(request()));

        verifyNoInteractions(policySnapshotPort, fieldCatalogPort, commandGateway);
    }

    private MaintenanceFieldDraftRequest request() {
        return new MaintenanceFieldDraftRequest(
                "maintenance-1", "POLICY_INFO_CHANGE",
                List.of(new FieldProposalInput(
                        null, "policy.holder.mobile", PolicyFieldDataType.TEXT, "13900000000")),
                "operator-1", "tenant-1");
    }

    private MaintenanceView caseView() {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("maintenance-1");
        view.setPolicyId("policy-1");
        view.setTenantId("tenant-1");
        view.setIndependentCase(true);
        view.setInitializationCompleted(true);
        return view;
    }

    private PolicyMaintenanceSnapshot policySnapshot() {
        return policySnapshot(PolicyStatus.EFFECTIVE);
    }

    private PolicyMaintenanceSnapshot policySnapshot(PolicyStatus status) {
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P202608240001", CustomerId.of("customer-1"),
                "product-1", "product-v1", "plan-v1", status, 7,
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
                new MaintenanceSnapshotReference(
                        "axon-event://policy/tenant-1/policy-1?version=7", "a".repeat(64), 7,
                        OffsetDateTime.parse("2026-08-24T08:00:00Z")),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13800000000")));
    }

    private PolicyFieldCatalogEvidence fieldCatalog() {
        PolicyFieldDescriptorEvidence descriptor = new PolicyFieldDescriptorEvidence(
                "policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                "policy.field.holder.mobile", false, null,
                new PolicyFieldCapabilityEvidence(true, true, true, false, false, "POLICY_INFO_CHANGE"),
                PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE, null);
        return new PolicyFieldCatalogEvidence(
                "tenant-1", null, null, LocalDate.of(2026, 8, 1),
                "catalog-v1", "b".repeat(64), List.of(descriptor));
    }
}
