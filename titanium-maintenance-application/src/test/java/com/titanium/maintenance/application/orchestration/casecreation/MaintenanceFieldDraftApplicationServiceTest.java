package com.titanium.maintenance.application.orchestration.casecreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.titanium.maintenance.application.command.field.RecordMaintenanceFieldChangesInput.FieldProposalInput;
import com.titanium.maintenance.command.ProposeMaintenanceFieldChangesCommand;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCapabilityEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
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
        assertEquals(PolicyFieldValueType.TEXT, command.fieldCatalogSnapshot()
                .requireField("policy.holder.email").valueType());
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
    void shouldRejectFieldWithoutExecutorBeforeSendingCommand() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        // 邮箱字段可提案但无执行器，改造前会被受理、直到「生效」环节才失败
        MaintenanceFieldDraftRequest notExecutable = new MaintenanceFieldDraftRequest(
                "maintenance-1", "POLICY_INFO_CHANGE",
                List.of(new FieldProposalInput(
                        null, "policy.holder.email", PolicyFieldDataType.TEXT, "holder@example.com")),
                "operator-1", "tenant-1");

        MaintenanceValidationException exception = assertThrows(
                MaintenanceValidationException.class, () -> service.record(notExecutable));

        assertTrue(exception.getMessage().contains("字段尚未开放真实执行: policy.holder.email"));
        verifyNoInteractions(commandGateway);
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
        // 🔴 手机号在 Policy 真实目录中为 executable（PolicyFieldCatalog.java:75），夹具须与之一致，
        // 否则用例断言的是「提案一个永不具执行能力的字段仍被受理」这一线上不存在的场景。
        PolicyFieldDescriptorEvidence mobile = new PolicyFieldDescriptorEvidence(
                "policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                "policy.field.holder.mobile", false, null,
                new PolicyFieldCapabilityEvidence(true, true, true, true, false, "POLICY_INFO_CHANGE"),
                PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE, null);
        // 邮箱在真实目录中为 proposal(...)（PolicyFieldCatalog.java:78），即「可提案、无执行器」。
        PolicyFieldDescriptorEvidence email = new PolicyFieldDescriptorEvidence(
                "policy.holder.email", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                "policy.field.holder.email", false, null,
                new PolicyFieldCapabilityEvidence(true, true, true, false, false, "POLICY_INFO_CHANGE"),
                PolicyFieldSensitivityLevel.PUBLIC, PolicyFieldMaskingPolicy.NONE, null);
        return new PolicyFieldCatalogEvidence(
                "tenant-1", null, null, LocalDate.of(2026, 8, 1),
                "catalog-v1", "b".repeat(64), List.of(mobile, email));
    }
}
