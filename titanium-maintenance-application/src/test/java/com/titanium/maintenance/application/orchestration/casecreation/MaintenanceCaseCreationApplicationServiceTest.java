package com.titanium.maintenance.application.orchestration.casecreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.maintenance.command.AddMaintenanceItemCommand;
import com.titanium.maintenance.command.CompleteMaintenanceCaseInitializationCommand;
import com.titanium.maintenance.command.CreateMaintenanceCaseCommand;
import com.titanium.maintenance.command.ScheduleMaintenanceEffectCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.PolicyMaintenanceSnapshotFailureReason;
import com.titanium.maintenance.common.enums.ProductMaintenanceOfferingFailureReason;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.common.exception.PolicyMaintenanceSnapshotException;
import com.titanium.maintenance.common.exception.ProductMaintenanceOfferingException;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
import com.titanium.maintenance.port.ProductMaintenanceOfferingPort;
import com.titanium.maintenance.port.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingEvidence;
import com.titanium.maintenance.port.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingRequest;
import com.titanium.maintenance.port.TenantTimeZonePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

@ExtendWith(MockitoExtension.class)
class MaintenanceCaseCreationApplicationServiceTest {

    @Mock private PolicyMaintenanceSnapshotPort policyMaintenanceSnapshotPort;
    @Mock private ProductMaintenanceOfferingPort productMaintenanceOfferingPort;
    @Mock private MaintenanceItemConfigurationRepository configurationRepository;
    @Mock private CommandGateway commandGateway;
    @Mock private MaintenanceViewRepository maintenanceViewRepository;
    @Mock private TenantTimeZonePort tenantTimeZonePort;

    private MaintenanceCaseCreationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceCaseCreationApplicationService(
                policyMaintenanceSnapshotPort, productMaintenanceOfferingPort,
                configurationRepository, commandGateway, maintenanceViewRepository, tenantTimeZonePort);
    }

    @Test
    void shouldDispatchCommandAndReturnStableCaseId() {
        PolicyMaintenanceSnapshot snapshot = snapshot("tenant-1", "policy-1", PolicyStatus.EFFECTIVE);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        allowOfferingAndConfiguration(snapshot);
        when(commandGateway.send(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        String caseId = service.create(request()).join();

        ArgumentCaptor<CreateMaintenanceCaseCommand> captor =
                ArgumentCaptor.forClass(CreateMaintenanceCaseCommand.class);
        verify(commandGateway).send(captor.capture());
        CreateMaintenanceCaseCommand command = captor.getValue();
        assertEquals(command.id().id(), caseId);
        assertEquals("customer-from-policy", command.customerId().id());
        assertEquals(snapshot, command.policySnapshot());
        assertEquals(List.of("POLICY_INFO_CHANGE"), command.selectedItemCodes());
        verify(commandGateway).send(any(AddMaintenanceItemCommand.class));
        verify(commandGateway).send(any(CompleteMaintenanceCaseInitializationCommand.class));
        verify(policyMaintenanceSnapshotPort).capture(snapshotRequest());
        verify(productMaintenanceOfferingPort).resolve(offeringRequest(snapshot));
    }

    @Test
    void shouldResolveAndFreezeMultipleItemsInCommandOrder() {
        PolicyMaintenanceSnapshot snapshot = snapshot("tenant-1", "policy-1", PolicyStatus.EFFECTIVE);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE", "BENEFICIARY_CHANGE")));
        allowConfiguration(snapshot, "POLICY_INFO_CHANGE", Set.of(), false);
        allowConfiguration(snapshot, "BENEFICIARY_CHANGE", Set.of(), false);
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.create(request(List.of("POLICY_INFO_CHANGE", "BENEFICIARY_CHANGE"))).join();

        InOrder order = inOrder(commandGateway);
        order.verify(commandGateway).send(any(CreateMaintenanceCaseCommand.class));
        order.verify(commandGateway, times(2)).send(any(AddMaintenanceItemCommand.class));
        order.verify(commandGateway).send(any(CompleteMaintenanceCaseInitializationCommand.class));
        ArgumentCaptor<AddMaintenanceItemCommand> itemCaptor =
                ArgumentCaptor.forClass(AddMaintenanceItemCommand.class);
        verify(commandGateway, times(2)).send(itemCaptor.capture());
        assertEquals(
                List.of("POLICY_INFO_CHANGE", "BENEFICIARY_CHANGE"),
                itemCaptor.getAllValues().stream()
                        .map(command -> command.definition().itemCode())
                        .toList());
        itemCaptor.getAllValues().forEach(command -> assertEquals(
                "offering-1", command.selectionEvidence().offeringId()));
    }

    @Test
    void shouldCreateFutureScheduleUsingFrozenTenantZoneAndUtcExecutionTime() {
        PolicyMaintenanceSnapshot snapshot = snapshot("tenant-1", "policy-1", PolicyStatus.EFFECTIVE);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE")));
        allowConfiguration(snapshot, "POLICY_INFO_CHANGE", Set.of(), false,
                new MaintenanceEffectiveRule(
                        Set.of(EffectiveTimeType.FUTURE), EffectiveTimeType.FUTURE, 0, 3650));
        when(tenantTimeZonePort.resolveZoneId("tenant-1")).thenReturn("Asia/Shanghai");
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        LocalDateTime tenantEffectiveAt = LocalDateTime.of(2030, 1, 2, 9, 30);
        MaintenanceCaseCreationRequest request = new MaintenanceCaseCreationRequest(
                "policy-1", List.of("POLICY_INFO_CHANGE"), EffectiveTimeType.FUTURE,
                tenantEffectiveAt, "未来联系方式变更", "request-future", MaintenanceChannel.API,
                "api-client-1", "tenant-1");

        service.create(request).join();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, times(4)).send(captor.capture());
        ScheduleMaintenanceEffectCommand schedule = captor.getAllValues().stream()
                .filter(ScheduleMaintenanceEffectCommand.class::isInstance)
                .map(ScheduleMaintenanceEffectCommand.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("Asia/Shanghai", schedule.tenantZoneId());
        assertEquals(LocalDateTime.of(2030, 1, 2, 1, 30), schedule.nextExecutionAt());
    }

    @Test
    void shouldCreateRetroactiveCaseWithinFrozenConfigurationBoundary() {
        PolicyMaintenanceSnapshot snapshot = retroactiveSnapshot();
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE")));
        allowConfiguration(snapshot, "POLICY_INFO_CHANGE", Set.of(), false,
                new MaintenanceEffectiveRule(
                        Set.of(EffectiveTimeType.RETROACTIVE), EffectiveTimeType.RETROACTIVE, 30, 0));
        when(tenantTimeZonePort.resolveZoneId("tenant-1")).thenReturn("Asia/Shanghai");
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        LocalDateTime effectiveAt = LocalDateTime.now(ZoneOffset.ofHours(8)).minusDays(10);

        service.create(retroactiveRequest(effectiveAt)).join();

        ArgumentCaptor<CreateMaintenanceCaseCommand> command =
                ArgumentCaptor.forClass(CreateMaintenanceCaseCommand.class);
        verify(commandGateway).send(command.capture());
        assertEquals(EffectiveTimeType.RETROACTIVE, command.getValue().effectiveTimeType());
        assertEquals(effectiveAt, command.getValue().specificEffectiveDate());
        verify(commandGateway, never()).send(any(ScheduleMaintenanceEffectCommand.class));
    }

    @Test
    void shouldRejectRetroactiveCaseBeyondFrozenConfigurationBoundary() {
        PolicyMaintenanceSnapshot snapshot = retroactiveSnapshot();
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE")));
        allowConfiguration(snapshot, "POLICY_INFO_CHANGE", Set.of(), false,
                new MaintenanceEffectiveRule(
                        Set.of(EffectiveTimeType.RETROACTIVE), EffectiveTimeType.RETROACTIVE, 30, 0));
        when(tenantTimeZonePort.resolveZoneId("tenant-1")).thenReturn("Asia/Shanghai");

        assertThrows(MaintenanceValidationException.class, () -> service.create(retroactiveRequest(
                LocalDateTime.now(ZoneOffset.ofHours(8)).minusDays(31))));

        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldRejectRetroactiveModeOutsideFrozenConfiguration() {
        PolicyMaintenanceSnapshot snapshot = retroactiveSnapshot();
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        allowOfferingAndConfiguration(snapshot);

        assertThrows(MaintenanceValidationException.class, () -> service.create(retroactiveRequest(
                LocalDateTime.now(ZoneOffset.ofHours(8)).minusDays(1))));

        verifyNoInteractions(commandGateway, tenantTimeZonePort);
    }

    @Test
    void shouldRecoverConcurrentCreateFromIdempotencyProjection() {
        PolicyMaintenanceSnapshot snapshot = snapshot("tenant-1", "policy-1", PolicyStatus.EFFECTIVE);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        allowOfferingAndConfiguration(snapshot);
        CreateMaintenanceCaseCommand expected = CreateMaintenanceCaseCommand.of(
                "policy-1", List.of("POLICY_INFO_CHANGE"), EffectiveTimeType.IMMEDIATE,
                null, "联系方式变更", snapshot, "request-1", MaintenanceChannel.API,
                "api-client-1", "tenant-1");
        MaintenanceView winner = new MaintenanceView();
        winner.setMaintenanceId(expected.id().id());
        winner.setRequestFingerprint(expected.requestFingerprint());
        when(maintenanceViewRepository.findByTenantIdAndSourceAndClientRequestKeyAndIndependentCaseTrue(
                "tenant-1", MaintenanceChannel.API, "request-1"))
                .thenReturn(Optional.of(winner));
        when(commandGateway.send(any()))
                .thenReturn(
                        CompletableFuture.failedFuture(
                                new AggregateStreamCreationException("concurrent create")),
                        CompletableFuture.completedFuture(null),
                        CompletableFuture.completedFuture(null));

        String caseId = service.create(request()).join();

        assertEquals(expected.id().id(), caseId);
        verify(commandGateway, times(3)).send(any());
    }

    @Test
    void shouldNotCompleteInitializationWhenAddingAnyItemFails() {
        PolicyMaintenanceSnapshot snapshot = snapshot("tenant-1", "policy-1", PolicyStatus.EFFECTIVE);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        allowOfferingAndConfiguration(snapshot);
        when(commandGateway.send(any(CreateMaintenanceCaseCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(commandGateway.send(any(AddMaintenanceItemCommand.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("add failed")));

        assertThrows(CompletionException.class, () -> service.create(request()).join());

        verify(commandGateway, never()).send(any(CompleteMaintenanceCaseInitializationCommand.class));
    }

    @Test
    void shouldRejectMutuallyExclusiveItemsBeforeCreatingAggregate() {
        PolicyMaintenanceSnapshot snapshot = snapshot("tenant-1", "policy-1", PolicyStatus.EFFECTIVE);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE", "POLICY_TERMINATION")));
        allowConfiguration(snapshot, "POLICY_INFO_CHANGE", Set.of("POLICY_TERMINATION"), false);
        allowConfiguration(snapshot, "POLICY_TERMINATION", Set.of(), false);

        assertThrows(MaintenanceValidationException.class, () -> service.create(
                request(List.of("POLICY_INFO_CHANGE", "POLICY_TERMINATION"))));

        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldRejectAtomicOnlyItemCombinationBeforeCreatingAggregate() {
        PolicyMaintenanceSnapshot snapshot = snapshot("tenant-1", "policy-1", PolicyStatus.EFFECTIVE);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE", "POLICY_TERMINATION")));
        allowConfiguration(snapshot, "POLICY_INFO_CHANGE", Set.of(), false);
        allowConfiguration(snapshot, "POLICY_TERMINATION", Set.of(), true);

        assertThrows(MaintenanceValidationException.class, () -> service.create(
                request(List.of("POLICY_INFO_CHANGE", "POLICY_TERMINATION"))));

        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldFailClosedWhenSnapshotIsMissing() {
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(null);

        PolicyMaintenanceSnapshotException exception = assertThrows(
                PolicyMaintenanceSnapshotException.class, () -> service.create(request()));

        assertEquals(PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE, exception.getReason());
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldFailClosedWhenSnapshotTenantDoesNotMatch() {
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest()))
                .thenReturn(snapshot("tenant-2", "policy-1", PolicyStatus.EFFECTIVE));

        PolicyMaintenanceSnapshotException exception = assertThrows(
                PolicyMaintenanceSnapshotException.class, () -> service.create(request()));

        assertEquals(PolicyMaintenanceSnapshotFailureReason.TENANT_MISMATCH, exception.getReason());
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldFailClosedWhenSnapshotPolicyDoesNotMatch() {
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest()))
                .thenReturn(snapshot("tenant-1", "policy-2", PolicyStatus.EFFECTIVE));

        PolicyMaintenanceSnapshotException exception = assertThrows(
                PolicyMaintenanceSnapshotException.class, () -> service.create(request()));

        assertEquals(PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID, exception.getReason());
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldCreateSuspendedPolicyCaseWhenOfferingAllowsState() {
        PolicyMaintenanceSnapshot snapshot = snapshot(
                "tenant-1", "policy-1", PolicyStatus.SUSPENDED);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        allowOfferingAndConfiguration(snapshot);
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        String caseId = service.create(request()).join();

        verify(productMaintenanceOfferingPort).resolve(offeringRequest(snapshot));
        ArgumentCaptor<CreateMaintenanceCaseCommand> command =
                ArgumentCaptor.forClass(CreateMaintenanceCaseCommand.class);
        verify(commandGateway).send(command.capture());
        assertEquals(command.getValue().id().id(), caseId);
        assertEquals(PolicyStatus.SUSPENDED, command.getValue().policySnapshot().policyStatus());
    }

    @Test
    void shouldPropagateSnapshotPortFailureWithoutSendingCommand() {
        PolicyMaintenanceSnapshotException failure = new PolicyMaintenanceSnapshotException(
                PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE, "Policy快照服务不可用");
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenThrow(failure);

        PolicyMaintenanceSnapshotException actual = assertThrows(
                PolicyMaintenanceSnapshotException.class, () -> service.create(request()));

        assertEquals(failure, actual);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldRejectItemOutsideProductOffering() {
        PolicyMaintenanceSnapshot snapshot = snapshot("tenant-1", "policy-1", PolicyStatus.EFFECTIVE);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("BENEFICIARY_CHANGE")));

        ProductMaintenanceOfferingException exception = assertThrows(
                ProductMaintenanceOfferingException.class, () -> service.create(request()));

        assertEquals(ProductMaintenanceOfferingFailureReason.NOT_APPLICABLE, exception.getReason());
        verifyNoInteractions(configurationRepository, commandGateway);
    }

    @Test
    void shouldRejectWhenPublishedConfigurationIsMissing() {
        PolicyMaintenanceSnapshot snapshot = snapshot("tenant-1", "policy-1", PolicyStatus.EFFECTIVE);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE")));
        when(configurationRepository.findEffective(
                "tenant-1", "POLICY_INFO_CHANGE", snapshot.businessEffectiveAt().toLocalDateTime()))
                .thenReturn(Optional.empty());

        assertThrows(MaintenanceConfigurationNotFoundException.class, () -> service.create(request()));

        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldRejectWhenConfigurationDoesNotSupportChannel() {
        PolicyMaintenanceSnapshot snapshot = snapshot("tenant-1", "policy-1", PolicyStatus.EFFECTIVE);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE")));
        StoredConfiguration stored = storedConfiguration(
                "POLICY_INFO_CHANGE", Set.of(MaintenanceChannel.MANUAL), Set.of(), false);
        when(configurationRepository.findEffective(
                "tenant-1", "POLICY_INFO_CHANGE", snapshot.businessEffectiveAt().toLocalDateTime()))
                .thenReturn(Optional.of(stored));

        ProductMaintenanceOfferingException exception = assertThrows(
                ProductMaintenanceOfferingException.class, () -> service.create(request()));

        assertEquals(ProductMaintenanceOfferingFailureReason.NOT_APPLICABLE, exception.getReason());
        verifyNoInteractions(commandGateway);
    }

    private MaintenanceCaseCreationRequest request() {
        return request(List.of("POLICY_INFO_CHANGE"));
    }

    private MaintenanceCaseCreationRequest request(List<String> itemCodes) {
        return new MaintenanceCaseCreationRequest(
                "policy-1", itemCodes, EffectiveTimeType.IMMEDIATE,
                null, "联系方式变更", "request-1", MaintenanceChannel.API,
                "api-client-1", "tenant-1");
    }

    private MaintenanceCaseCreationRequest retroactiveRequest(LocalDateTime effectiveAt) {
        return new MaintenanceCaseCreationRequest(
                "policy-1", List.of("POLICY_INFO_CHANGE"), EffectiveTimeType.RETROACTIVE,
                effectiveAt, "追溯联系方式变更", "request-retroactive", MaintenanceChannel.API,
                "api-client-1", "tenant-1");
    }

    private PolicyMaintenanceSnapshotRequest snapshotRequest() {
        return new PolicyMaintenanceSnapshotRequest("policy-1", "tenant-1");
    }

    private ProductMaintenanceOfferingRequest offeringRequest(PolicyMaintenanceSnapshot snapshot) {
        return new ProductMaintenanceOfferingRequest(
                "tenant-1", "product-1", "product-v3", "plan-v2",
                snapshot.policyStatus(), MaintenanceChannel.API, snapshot.businessEffectiveAt());
    }

    private ProductMaintenanceOfferingEvidence offering(Set<String> itemCodes) {
        OffsetDateTime resolvedAt = OffsetDateTime.of(2026, 8, 24, 11, 31, 0, 0, ZoneOffset.ofHours(8));
        return new ProductMaintenanceOfferingEvidence(
                "tenant-1", "product-1", "product-v3", "plan-v2", "offering-1", "offering-v1",
                "b".repeat(64), resolvedAt, itemCodes);
    }

    private void allowOfferingAndConfiguration(PolicyMaintenanceSnapshot snapshot) {
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE")));
        allowConfiguration(snapshot, "POLICY_INFO_CHANGE", Set.of(), false);
    }

    private void allowConfiguration(
            PolicyMaintenanceSnapshot snapshot,
            String itemCode,
            Set<String> incompatibleItemCodes,
            boolean atomicOnly) {
        allowConfiguration(snapshot, itemCode, incompatibleItemCodes, atomicOnly,
                MaintenanceEffectiveRule.immediate());
    }

    private void allowConfiguration(
            PolicyMaintenanceSnapshot snapshot,
            String itemCode,
            Set<String> incompatibleItemCodes,
            boolean atomicOnly,
            MaintenanceEffectiveRule effectiveRule) {
        StoredConfiguration stored = storedConfiguration(
                itemCode, Set.of(MaintenanceChannel.API), incompatibleItemCodes, atomicOnly, effectiveRule);
        when(configurationRepository.findEffective(
                "tenant-1", itemCode, snapshot.businessEffectiveAt().toLocalDateTime()))
                .thenReturn(Optional.of(stored));
    }

    private StoredConfiguration storedConfiguration(
            String itemCode,
            Set<MaintenanceChannel> channels,
            Set<String> incompatibleItemCodes,
            boolean atomicOnly) {
        return storedConfiguration(
                itemCode, channels, incompatibleItemCodes, atomicOnly, MaintenanceEffectiveRule.immediate());
    }

    private StoredConfiguration storedConfiguration(
            String itemCode,
            Set<MaintenanceChannel> channels,
            Set<String> incompatibleItemCodes,
            boolean atomicOnly,
            MaintenanceEffectiveRule effectiveRule) {
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                itemCode, "1.0.0", itemCode, MaintenanceItemCategory.BASIC_INFORMATION,
                channels, List.of(), List.of(
                        MaintenanceStepDefinition.skipped(1, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(2, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, effectiveRule,
                incompatibleItemCodes, atomicOnly);
        MaintenanceItemConfiguration configuration = mock(MaintenanceItemConfiguration.class);
        lenient().when(configuration.getConfigurationId()).thenReturn("configuration-" + itemCode);
        when(configuration.getDefinition()).thenReturn(definition);
        lenient().when(configuration.getContentHash()).thenReturn("c".repeat(64));
        when(configuration.isEffectiveAt(any())).thenReturn(true);
        return new StoredConfiguration(configuration, 1L);
    }

    private PolicyMaintenanceSnapshot snapshot(
            String tenantId,
            String policyId,
            PolicyStatus status) {
        OffsetDateTime capturedAt = OffsetDateTime.of(2026, 8, 24, 11, 30, 0, 0, ZoneOffset.ofHours(8));
        return new PolicyMaintenanceSnapshot(
                tenantId, PolicyId.of(policyId), "P202608240001", CustomerId.of("customer-from-policy"),
                "product-1", "product-v3", "plan-v2", status, 7L, capturedAt,
                new MaintenanceSnapshotReference(
                        "policy/" + policyId + "/versions/7", "a".repeat(64), 7L, capturedAt),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13800000000")));
    }

    private PolicyMaintenanceSnapshot retroactiveSnapshot() {
        OffsetDateTime capturedAt = OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1);
        OffsetDateTime businessEffectiveAt = capturedAt.minusYears(1);
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P202608240001", CustomerId.of("customer-from-policy"),
                "product-1", "product-v3", "plan-v2", PolicyStatus.EFFECTIVE, 7L, businessEffectiveAt,
                new MaintenanceSnapshotReference(
                        "policy/policy-1/versions/7", "a".repeat(64), 7L, capturedAt),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13800000000")));
    }
}
