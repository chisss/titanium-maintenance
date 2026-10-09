package com.titanium.maintenance.application.orchestration.casecreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
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
import com.titanium.maintenance.common.exception.MaintenancePolicyStatusNotAdmittedException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.common.exception.PolicyMaintenanceSnapshotException;
import com.titanium.maintenance.common.exception.ProductMaintenanceOfferingException;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
import com.titanium.maintenance.port.product.ProductMaintenanceOfferingPort;
import com.titanium.maintenance.port.product.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingEvidence;
import com.titanium.maintenance.port.product.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingRequest;
import com.titanium.maintenance.port.tenant.TenantTimeZonePort;
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
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

@ExtendWith(MockitoExtension.class)
class MaintenanceCaseCreationApplicationServiceTest {

    @Mock private PolicyMaintenanceSnapshotPort policyMaintenanceSnapshotPort;
    @Mock private ProductMaintenanceOfferingPort productMaintenanceOfferingPort;
    @Mock private MaintenanceItemConfigurationRepository configurationRepository;
    @Mock private CommandGateway commandGateway;
    @Mock private MaintenanceViewRepository maintenanceViewRepository;
    @Mock private TenantTimeZonePort tenantTimeZonePort;
    @Mock private MaintenanceInFlightAdmissionPolicy inFlightAdmissionPolicy;

    private MaintenanceCaseCreationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceCaseCreationApplicationService(
                policyMaintenanceSnapshotPort, productMaintenanceOfferingPort,
                configurationRepository, commandGateway, maintenanceViewRepository, tenantTimeZonePort,
                inFlightAdmissionPolicy);
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

    /**
     * 🔴 同义守卫：NEXT_PERIOD（次期生效）与 POLICY_ANNIVERSARY（保单周年日）必须解析到**同一执行时刻**。
     *
     * <p>
     * 两者同义的判据是「同一快照字段、同一日期、同一调度路径」。本用例把两个生效方式各建一次案件，
     * 取排期命令里的执行时刻**互比**（不是各自与写死值比），任一方另接日期来源（例如 NEXT_PERIOD 错接到
     * 次缴费日）两值立刻分叉。夹具中次缴费日与保单周年日**刻意取不同值**，否则「错接 nextBillingDateAt」
     * 也能侥幸通过——夹具无区分力时用例恒真。
     * </p>
     */
    @Test
    void shouldResolveNextPeriodToTheSameExecutionDateAsPolicyAnniversary() {
        OffsetDateTime anniversary = OffsetDateTime.of(2027, 8, 24, 11, 30, 0, 0, ZoneOffset.ofHours(8));
        PolicyMaintenanceSnapshot snapshot = anniversarySnapshot(anniversary);

        stubScheduledCreation(snapshot, EffectiveTimeType.POLICY_ANNIVERSARY);
        LocalDateTime anniversaryExecutionAt = createAndCaptureExecutionAt(
                EffectiveTimeType.POLICY_ANNIVERSARY, "request-anniversary");

        clearInvocations(commandGateway);
        stubScheduledCreation(snapshot, EffectiveTimeType.NEXT_PERIOD);
        LocalDateTime nextPeriodExecutionAt = createAndCaptureExecutionAt(
                EffectiveTimeType.NEXT_PERIOD, "request-next-period");

        assertEquals(anniversaryExecutionAt, nextPeriodExecutionAt);
        // 独立见证：保单周年日 2027-08-24T11:30+08:00 换算 UTC 即 03:30（该值不由被测代码推导）
        assertEquals(LocalDateTime.of(2027, 8, 24, 3, 30), nextPeriodExecutionAt);
    }

    /**
     * 🔴 零副作用守卫：排期解析在建案命令**之前**完成，故解析失败时一条命令都不许发出。
     *
     * <p>
     * 原实现把解析放在命令链尾：建案/加项/完成初始化三条命令均已落库，随后解析抛错 ⇒ HTTP 报失败，
     * 库里却留下一张无排期、永不执行、永不终态的 PENDING 案件；跨案在途门面按<b>保单</b>拦截，
     * 该保单自此永久无法建案（真机实测：案件 {@code d4e7544a} 落在保单 {@code a16ddd86} 上，
     * 其后同保单建案被 71001003「存在在途保全案件」拒绝）。
     * </p>
     * <p>
     * 判据必须同时含「抛什么」与「发没发」两侧：只断言抛异常无法区分「建案前拒绝」与
     * 「建案后拒绝」——后者正是缺陷形态，且同样抛 {@link MaintenanceValidationException}。
     * </p>
     */
    @Test
    void shouldRejectMissingAnniversaryBeforeSendingAnyCommand() {
        PolicyMaintenanceSnapshot snapshot = anniversarySnapshot(null);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE")));
        allowConfiguration(snapshot, "POLICY_INFO_CHANGE", Set.of(), false,
                new MaintenanceEffectiveRule(
                        Set.of(EffectiveTimeType.NEXT_PERIOD), EffectiveTimeType.NEXT_PERIOD, 0, 3650));
        when(tenantTimeZonePort.resolveZoneId("tenant-1")).thenReturn("Asia/Shanghai");
        // lenient：正常路径下命令网关零交互、本桩不被消费。留着它是为了让**缺陷回归**时归因精确——
        // 若解析被挪回命令链尾，建案命令会先发出并消费本桩，链条其后走到解析处抛真因，
        // 失败点落在 verifyNoInteractions（=「命令已发出」）而不是 NullPointerException。
        lenient().when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        MaintenanceValidationException failure = assertThrows(
                MaintenanceValidationException.class,
                () -> service.create(new MaintenanceCaseCreationRequest(
                        "policy-1", List.of("POLICY_INFO_CHANGE"), EffectiveTimeType.NEXT_PERIOD, null,
                        "周年日缺失必须建案前拒绝", "request-missing-anniversary",
                        MaintenanceChannel.API, "api-client-1", "tenant-1")));

        assertTrue(failure.getMessage().contains("Policy 未提供可用的下一保单年度起点"), failure.getMessage());
        verifyNoInteractions(commandGateway);
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

    /**
     * g04-07：Offering 放行的保单状态**不再**是唯一判据——字段类保全项的领域准入集合是 {@code {EFFECTIVE}}，
     * Offering 只可在此之上额外收窄，不得放宽。
     *
     * <p>
     * 本用例原为 {@code shouldCreateSuspendedPolicyCaseWhenOfferingAllowsState}，断言「Offering 允许 SUSPENDED
     * ⇒ 建案成功」——那正是 g04-07 要修的缺陷本身：Offering 是可配数据，能声明出与保单域状态机不相容的组合
     * （真机两条已发布 Offering 即为 {@code ["EFFECTIVE","PENDING_EFFECTIVE","SUSPENDED"]}）。现按其反面断言拒绝。
     * </p>
     */
    @Test
    void shouldRejectFieldItemWhenOfferingAllowsNonAdmittedPolicyStatus() {
        PolicyMaintenanceSnapshot snapshot = snapshot(
                "tenant-1", "policy-1", PolicyStatus.SUSPENDED);
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        allowOfferingAndConfiguration(snapshot);

        MaintenancePolicyStatusNotAdmittedException exception = assertThrows(
                MaintenancePolicyStatusNotAdmittedException.class, () -> service.create(request()));

        // POLICY_INFO_CHANGE 属既有 7 个字段类，沿用原对外契约错误码 POLICY_NOT_ACTIVE（非新码 71007013）
        assertEquals(MaintenanceErrorCode.POLICY_NOT_ACTIVE, exception.getErrorCode());
        verifyNoInteractions(commandGateway);
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

    /** 为指定生效方式铺好全部桩（供同义用例逐轮复用；每轮全部桩都会被消费，不触发严格桩告警）。 */
    private void stubScheduledCreation(PolicyMaintenanceSnapshot snapshot, EffectiveTimeType effectiveTimeType) {
        when(policyMaintenanceSnapshotPort.capture(snapshotRequest())).thenReturn(snapshot);
        when(productMaintenanceOfferingPort.resolve(offeringRequest(snapshot)))
                .thenReturn(offering(Set.of("POLICY_INFO_CHANGE")));
        allowConfiguration(snapshot, "POLICY_INFO_CHANGE", Set.of(), false,
                new MaintenanceEffectiveRule(Set.of(effectiveTimeType), effectiveTimeType, 0, 3650));
        when(tenantTimeZonePort.resolveZoneId("tenant-1")).thenReturn("Asia/Shanghai");
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    /** 以给定生效方式建案，回读排期命令中的 UTC 执行时刻（建案共 4 次命令：创建/加项/完成初始化/排期）。 */
    private LocalDateTime createAndCaptureExecutionAt(
            EffectiveTimeType effectiveTimeType, String clientRequestKey) {
        service.create(new MaintenanceCaseCreationRequest(
                "policy-1", List.of("POLICY_INFO_CHANGE"), effectiveTimeType, null,
                "次期生效同义性验证", clientRequestKey, MaintenanceChannel.API, "api-client-1", "tenant-1")).join();
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, times(4)).send(captor.capture());
        return captor.getAllValues().stream()
                .filter(ScheduleMaintenanceEffectCommand.class::isInstance)
                .map(ScheduleMaintenanceEffectCommand.class::cast)
                .findFirst()
                .orElseThrow()
                .nextExecutionAt();
    }

    /**
     * 保单周年日与次缴费日**刻意取不同值**（相差 3 个月），用于分辨「错接快照字段」——
     * 若两者同值，把 NEXT_PERIOD 接到 nextBillingDateAt 的缺陷会被本夹具掩盖。
     */
    private PolicyMaintenanceSnapshot anniversarySnapshot(OffsetDateTime nextPolicyAnniversaryAt) {
        OffsetDateTime capturedAt = OffsetDateTime.of(2026, 8, 24, 11, 30, 0, 0, ZoneOffset.ofHours(8));
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P202608240001", CustomerId.of("customer-from-policy"),
                "product-1", "product-v3", "plan-v2", PolicyStatus.EFFECTIVE, List.of(), 7L, capturedAt,
                capturedAt.plusMonths(3), nextPolicyAnniversaryAt,
                new MaintenanceSnapshotReference(
                        "policy/policy-1/versions/7", "a".repeat(64), 7L, capturedAt),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13800000000")));
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
