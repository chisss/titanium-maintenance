package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.MaintenancePremiumQuoteInput;
import com.titanium.maintenance.command.RecordMaintenancePremiumQuoteCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.port.MaintenanceUnderwritingPort;
import com.titanium.maintenance.port.PolicyServicePort;
import com.titanium.maintenance.port.ProductMaintenancePremiumQuotePort;
import com.titanium.maintenance.port.ProductMaintenancePremiumQuotePort.QuoteFact;
import com.titanium.maintenance.port.ProductMaintenancePremiumQuotePort.QuoteRequest;
import com.titanium.maintenance.port.ProductSurrenderValuePort;
import com.titanium.maintenance.port.ProductSurrenderValuePort.SurrenderFact;
import com.titanium.maintenance.port.ProductSurrenderValuePort.SurrenderRequest;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;

class MaintenancePremiumQuoteApplicationServiceTest {

    private CommandGateway commandGateway;
    private MaintenanceViewRepository maintenanceRepository;
    private MaintenanceWorkflowTaskViewRepository taskRepository;
    private MaintenanceCaseItemViewRepository itemRepository;
    private MaintenanceItemConfigurationRepository configurationRepository;
    private MaintenanceSnapshotViewRepository snapshotRepository;
    private ProductMaintenancePremiumQuotePort productPort;
    private PolicyServicePort policyPort;
    private ProductSurrenderValuePort surrenderPort;
    private MaintenanceWorkflowApplicationService service;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        maintenanceRepository = mock(MaintenanceViewRepository.class);
        taskRepository = mock(MaintenanceWorkflowTaskViewRepository.class);
        itemRepository = mock(MaintenanceCaseItemViewRepository.class);
        configurationRepository = mock(MaintenanceItemConfigurationRepository.class);
        snapshotRepository = mock(MaintenanceSnapshotViewRepository.class);
        productPort = mock(ProductMaintenancePremiumQuotePort.class);
        policyPort = mock(PolicyServicePort.class);
        surrenderPort = mock(ProductSurrenderValuePort.class);
        service = new MaintenanceWorkflowApplicationService(
                commandGateway, maintenanceRepository, taskRepository, itemRepository,
                mock(MaintenanceFieldChangeViewRepository.class), configurationRepository,
                new MaintenanceReviewPolicyEvaluator(), mock(MaintenanceUnderwritingPort.class),
                snapshotRepository, productPort, policyPort, surrenderPort,
                tenantId -> "Asia/Shanghai");
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void shouldRecordQuoteWithoutCompletingFeeTask() {
        MaintenanceView view = visibleContext(MaintenanceFeeMode.REQUIRED, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY, null);
        view.setBusinessEffectiveAt("2026-08-26T16:00:00Z");
        when(snapshotRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(snapshots()));
        when(productPort.quote(any())).thenAnswer(invocation -> quoteFact(invocation.getArgument(0), false));

        var result = service.quotePremium(input()).join();

        assertEquals(MaintenancePremiumQuoteStatus.QUOTED, result.status());
        ArgumentCaptor<RecordMaintenancePremiumQuoteCommand> captor =
                ArgumentCaptor.forClass(RecordMaintenancePremiumQuoteCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals(MaintenancePremiumQuoteStatus.QUOTED, captor.getValue().evidence().status());
        assertEquals(64, captor.getValue().evidence().requestPayloadHash().length());
    }

    @Test
    void shouldUseRetroactiveEffectiveTimeForProductQuote() {
        MaintenanceView view = visibleContext(MaintenanceFeeMode.REQUIRED, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY, null);
        LocalDateTime retroactiveAt = LocalDateTime.parse("2026-08-01T00:00:00");
        view.setEffectiveTimeType(EffectiveTimeType.RETROACTIVE);
        view.setSpecificEffectiveDate(retroactiveAt);
        when(snapshotRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(snapshots()));
        when(productPort.quote(any())).thenAnswer(invocation -> quoteFact(invocation.getArgument(0), false));

        service.quotePremium(input()).join();

        ArgumentCaptor<QuoteRequest> request = ArgumentCaptor.forClass(QuoteRequest.class);
        verify(productPort).quote(request.capture());
        assertEquals(retroactiveAt, request.getValue().businessTime());
    }

    @Test
    void shouldNotCallProductForNoneOrOptionalSkip() {
        visibleContext(MaintenanceFeeMode.NONE, MaintenanceStepMode.SKIPPED,
                MaintenanceWorkflowTaskStatus.SKIPPED, null);

        var noneResult = service.quotePremium(input()).join();

        assertEquals(MaintenancePremiumQuoteStatus.NOT_REQUIRED, noneResult.status());
        verify(productPort, never()).quote(any());

        setTask(MaintenanceStepMode.CONDITIONAL, MaintenanceWorkflowTaskStatus.SKIPPED,
                MaintenanceWorkflowConditionDecision.SKIP);
        setConfiguration(MaintenanceFeeMode.OPTIONAL);

        var optionalResult = service.quotePremium(input()).join();

        assertEquals(MaintenancePremiumQuoteStatus.NOT_REQUIRED, optionalResult.status());
        verify(productPort, never()).quote(any());
    }

    @Test
    void shouldRejectExpiredProductQuoteWithoutWritingEvent() {
        visibleContext(MaintenanceFeeMode.REQUIRED, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY, null);
        when(snapshotRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(snapshots()));
        when(productPort.quote(any())).thenAnswer(invocation -> quoteFact(invocation.getArgument(0), true));

        assertThrows(MaintenanceValidationException.class, () -> service.quotePremium(input()));

        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldUseProductSurrenderValueForTerminationQuote() {
        MaintenanceView view = visibleContext(MaintenanceFeeMode.REQUIRED, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY, null);
        view.setBusinessEffectiveAt("2026-08-26T16:00:00Z");
        setTask("SURRENDER", MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY, null);
        setItemAndConfiguration("SURRENDER", MaintenanceFeeMode.REQUIRED);
        when(policyPort.getPolicyFinancialSnapshot("policy-1", "tenant-1"))
                .thenReturn(new PolicyServicePort.PolicyFinancialSnapshot(
                        "product-1", "issuance-biz-1", LocalDate.parse("2026-01-01"),
                        new BigDecimal("121.20"), "CNY"));
        when(surrenderPort.calculate(any())).thenReturn(surrenderFact());

        var result = service.quotePremium(input()).join();

        assertEquals(MaintenancePremiumQuoteStatus.QUOTED, result.status());
        assertEquals(MaintenanceBalanceDirection.CREDIT, result.direction());
        assertEquals(new BigDecimal("121.20"), result.amount());
        verify(productPort, never()).quote(any());
        verify(snapshotRepository, never()).findByMaintenanceIdAndTenantId(any(), any());
        ArgumentCaptor<SurrenderRequest> request = ArgumentCaptor.forClass(SurrenderRequest.class);
        verify(surrenderPort).calculate(request.capture());
        assertEquals("issuance-biz-1", request.getValue().originalBizNo());
        assertEquals("original-calc", request.getValue().originalCalculationId());
        assertEquals(LocalDate.parse("2026-08-27"), request.getValue().surrenderDate());
        ArgumentCaptor<RecordMaintenancePremiumQuoteCommand> command =
                ArgumentCaptor.forClass(RecordMaintenancePremiumQuoteCommand.class);
        verify(commandGateway).send(command.capture());
        assertEquals("1".repeat(64), command.getValue().evidence().requestPayloadHash());
        assertEquals("pricing-plan-v2", command.getValue().evidence().pricingPlanVersion());
        assertEquals("3".repeat(64), command.getValue().evidence().pricingPlanContentHash());
    }

    @Test
    void shouldReuseOriginalSurrenderQuoteTimeForIdempotentReplay() {
        MaintenanceView view = visibleContext(MaintenanceFeeMode.REQUIRED, MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.READY, null);
        view.setBusinessEffectiveAt("2026-08-26T16:00:00Z");
        MaintenanceWorkflowTaskView task = setTask("SURRENDER", MaintenanceStepMode.REQUIRED,
                MaintenanceWorkflowTaskStatus.COMPLETED, null);
        LocalDateTime quotedAt = LocalDateTime.parse("2026-08-27T15:38:31");
        SurrenderFact fact = surrenderFact();
        task.setPremiumQuoteStatus(MaintenancePremiumQuoteStatus.QUOTED);
        task.setPremiumQuoteId(fact.adjustmentId());
        task.setPremiumQuoteRequestHash(fact.requestHash());
        task.setPremiumQuoteResultHash(fact.adjustmentResultHash());
        task.setPremiumQuotedAt(quotedAt);
        setItemAndConfiguration("SURRENDER", MaintenanceFeeMode.REQUIRED);
        when(policyPort.getPolicyFinancialSnapshot("policy-1", "tenant-1"))
                .thenReturn(new PolicyServicePort.PolicyFinancialSnapshot(
                        "product-1", "issuance-biz-1", LocalDate.parse("2026-01-01"),
                        new BigDecimal("121.20"), "CNY"));
        when(surrenderPort.calculate(any())).thenReturn(fact);

        service.quotePremium(input()).join();

        ArgumentCaptor<RecordMaintenancePremiumQuoteCommand> command =
                ArgumentCaptor.forClass(RecordMaintenancePremiumQuoteCommand.class);
        verify(commandGateway).send(command.capture());
        assertEquals(quotedAt, command.getValue().evidence().quotedAt());
        assertEquals(quotedAt.plusHours(24), command.getValue().evidence().validUntil());
    }

    private MaintenanceView visibleContext(
            MaintenanceFeeMode feeMode,
            MaintenanceStepMode stepMode,
            MaintenanceWorkflowTaskStatus status,
            MaintenanceWorkflowConditionDecision decision) {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("case-1");
        view.setTenantId("tenant-1");
        view.setPolicyId("policy-1");
        view.setPolicyBaselineVersion(7L);
        view.setProductId("product-1");
        view.setProductVersion("product-v3");
        view.setPlanVersion("plan-v2");
        view.setBusinessEffectiveAt("2026-08-25T09:00:00+08:00");
        view.setCreateTime(LocalDateTime.parse("2026-08-25T08:00:00"));
        when(maintenanceRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "case-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        setTask(stepMode, status, decision);

        MaintenanceCaseItemView item = new MaintenanceCaseItemView();
        item.setItemCode("COVERAGE_AMOUNT_CHANGE");
        item.setConfigurationId("configuration-1");
        item.setConfigurationVersion("config-v1");
        item.setConfigurationContentHash("a".repeat(64));
        when(itemRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                "tenant-1", "case-1", "COVERAGE_AMOUNT_CHANGE"))
                .thenReturn(Optional.of(item));
        setConfiguration(feeMode);
        return view;
    }

    private void setItemAndConfiguration(String itemCode, MaintenanceFeeMode feeMode) {
        MaintenanceCaseItemView item = new MaintenanceCaseItemView();
        item.setItemCode(itemCode);
        item.setConfigurationId("configuration-1");
        item.setConfigurationVersion("config-v1");
        item.setConfigurationContentHash("a".repeat(64));
        when(itemRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                "tenant-1", "case-1", itemCode)).thenReturn(Optional.of(item));
        setConfiguration(itemCode, feeMode);
    }

    private void setTask(
            MaintenanceStepMode mode,
            MaintenanceWorkflowTaskStatus status,
            MaintenanceWorkflowConditionDecision decision) {
        setTask("COVERAGE_AMOUNT_CHANGE", mode, status, decision);
    }

    private MaintenanceWorkflowTaskView setTask(
            String itemCode,
            MaintenanceStepMode mode,
            MaintenanceWorkflowTaskStatus status,
            MaintenanceWorkflowConditionDecision decision) {
        MaintenanceWorkflowTaskView task = new MaintenanceWorkflowTaskView();
        task.setTaskId("fee-task");
        task.setItemCode(itemCode);
        task.setStepType(MaintenanceStepType.FEE_SETTLEMENT);
        task.setMode(mode);
        task.setStatus(status);
        task.setConditionDecision(decision);
        task.setConditionDecidedAt(decision == null
                ? null
                : LocalDateTime.parse("2026-08-25T08:30:00"));
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "case-1", "fee-task"))
                .thenReturn(Optional.of(task));
        return task;
    }

    private void setConfiguration(MaintenanceFeeMode feeMode) {
        setConfiguration("COVERAGE_AMOUNT_CHANGE", feeMode);
    }

    private void setConfiguration(String itemCode, MaintenanceFeeMode feeMode) {
        MaintenanceItemConfiguration configuration = mock(MaintenanceItemConfiguration.class);
        MaintenanceItemDefinition definition = mock(MaintenanceItemDefinition.class);
        when(configuration.getConfigurationId()).thenReturn("configuration-1");
        when(configuration.getContentHash()).thenReturn("a".repeat(64));
        when(configuration.getDefinition()).thenReturn(definition);
        when(definition.itemCode()).thenReturn(itemCode);
        when(definition.version()).thenReturn("config-v1");
        when(definition.feeMode()).thenReturn(feeMode);
        when(configurationRepository.findById("tenant-1", "configuration-1"))
                .thenReturn(Optional.of(new StoredConfiguration(configuration, 1L)));
    }

    private MaintenanceSnapshotView snapshots() {
        MaintenanceSnapshotView view = new MaintenanceSnapshotView();
        view.setBeforeStorageKey("before.json");
        view.setBeforeContentHash("b".repeat(64));
        view.setBeforePolicyVersion(7L);
        view.setBeforeCapturedAt("2026-08-25T08:00:00+08:00");
        view.setProposedStorageKey("proposed.json");
        view.setProposedContentHash("c".repeat(64));
        view.setProposedPolicyVersion(7L);
        view.setProposedCapturedAt("2026-08-25T08:30:00+08:00");
        return view;
    }

    private MaintenancePremiumQuoteInput input() {
        return new MaintenancePremiumQuoteInput(
                "case-1", "fee-task", "operation-1", "ENDORSEMENT", "original-calc",
                "CNY", new BigDecimal("500000"), 35, "M", 10, 20, 12,
                java.util.Map.of("insured.occupation", "1"), List.of(), "agent", 3,
                "保额增加", "operator-1", "tenant-1", MaintenanceChannel.API);
    }

    private QuoteFact quoteFact(QuoteRequest request, boolean expired) {
        LocalDateTime quotedAt = expired
                ? LocalDateTime.parse("2020-01-01T10:00:00")
                : LocalDateTime.now().minusMinutes(1);
        return new QuoteFact(
                request.tenantId(), request.maintenanceId(), request.policyId(),
                request.policyBaselineVersion(), request.productId(), request.productVersion(),
                request.planVersion(), request.itemCode(), request.beforeSnapshot().contentHash(),
                request.proposedSnapshot().contentHash(), "quote-1", "d".repeat(64),
                request.originalCalculationId(), "e".repeat(64), "replacement-calc", "f".repeat(64),
                "plan-v2", "1".repeat(64), request.idempotencyKey(), request.payloadHash(),
                "2".repeat(64), "DEBIT 20 CNY; lines=1", MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20"), "CNY", quotedAt, quotedAt.plusHours(24));
    }

    private SurrenderFact surrenderFact() {
        return new SurrenderFact(
                "case-1", "LIFE-SURRENDER-CASH-VALUE", "V1.0", "a".repeat(64),
                3, 15, "COOLING_OFF", true, BigDecimal.ONE, new BigDecimal("121.20"),
                BigDecimal.ZERO, BigDecimal.ZERO, "original-calc", "e".repeat(64),
                "replacement-calc", "f".repeat(64), "adjustment-1", "1".repeat(64),
                "2".repeat(64), "pricing-plan-v2", "3".repeat(64),
                MaintenanceBalanceDirection.CREDIT, new BigDecimal("121.20"), "CNY");
    }
}
