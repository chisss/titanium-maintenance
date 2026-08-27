package com.titanium.maintenance.application.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.maintenance.application.model.MaintenancePremiumSettlementInput;
import com.titanium.maintenance.application.model.MaintenancePremiumSettlementResult;
import com.titanium.maintenance.application.model.MaintenanceReversalSettlementInput;
import com.titanium.maintenance.application.model.MaintenanceSurrenderSettlementInput;
import com.titanium.maintenance.application.model.MaintenanceSurrenderSettlementResult;
import com.titanium.maintenance.command.RecordMaintenanceFinancialSettlementCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumAdjustmentCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumPostingCommand;
import com.titanium.maintenance.command.RecordMaintenanceSurrenderValueCommand;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenancePremiumSettlementStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort;
import com.titanium.maintenance.port.PolicyServicePort;
import com.titanium.maintenance.port.ProductPremiumLifecyclePort;
import com.titanium.maintenance.port.ProductSurrenderValuePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

@ExtendWith(MockitoExtension.class)
class MaintenancePremiumSettlementOrchestratorTest {

    @Mock
    private CommandGateway commandGateway;
    @Mock
    private MaintenanceViewRepository repository;
    @Mock
    private PolicyServicePort policyPort;
    @Mock
    private ProductPremiumLifecyclePort productPort;
    @Mock
    private ProductSurrenderValuePort surrenderValuePort;
    @Mock
    private BillingPremiumLifecyclePort billingPort;

    private MaintenancePremiumSettlementOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new MaintenancePremiumSettlementOrchestrator(
                commandGateway, repository, policyPort, productPort, surrenderValuePort, billingPort);
    }

    @Test
    void shouldRejectGenericPremiumEndpointForSurrenderCase() {
        MaintenanceView view = view(MaintenancePremiumSettlementStatus.NOT_STARTED);
        view.setMaintenanceType(MaintenanceType.POLICY_TERMINATION);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1")).thenReturn(Optional.of(view));

        assertThrows(com.titanium.maintenance.common.exception.BusinessException.class,
                () -> orchestrator.settle("maintenance-1", "1", input()));

        verify(productPort, never()).calculateReplacement(any());
    }

    @Test
    void shouldCalculateSurrenderValueAndSettleRefund() {
        MaintenanceView view = view(MaintenancePremiumSettlementStatus.NOT_STARTED);
        view.setMaintenanceType(MaintenanceType.POLICY_TERMINATION);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1")).thenReturn(Optional.of(view));
        when(policyPort.getPolicyFinancialSnapshot("policy-1", "1")).thenReturn(
                new PolicyServicePort.PolicyFinancialSnapshot(
                        "product-1", "issuance-1", LocalDate.of(2026, 1, 1),
                        new BigDecimal("121.20"), "CNY"));
        when(surrenderValuePort.calculate(any())).thenReturn(surrenderFact());
        when(billingPort.post(any())).thenReturn(new BillingPremiumLifecyclePort.PostingFact(
                "posting-1", "adjustment-1", hash('b'), MaintenanceBalanceDirection.CREDIT,
                new BigDecimal("72.72"), "CNY", "POSTED", "refund-instruction-1", "refund-order-1",
                "SUCCEEDED", 1));

        MaintenanceSurrenderSettlementResult result = orchestrator.settleSurrender(
                "maintenance-1", "1", new MaintenanceSurrenderSettlementInput(
                        "calc-original", LocalDate.of(2026, 8, 20), 1,
                        LocalDateTime.of(2026, 8, 20, 12, 0), "寿险退保", "admin"));

        assertEquals(MaintenancePremiumSettlementStatus.SETTLED.name(),
                result.settlement().premiumSettlementStatus());
        assertEquals(new BigDecimal("0.60000000"), result.cashValueRate());
        verify(commandGateway).sendAndWait(any(RecordMaintenanceSurrenderValueCommand.class));
        verify(commandGateway).sendAndWait(any(RecordMaintenancePremiumPostingCommand.class));
    }

    @Test
    void shouldCalculateAdjustPostAndRecordBothCheckpoints() {
        MaintenanceView view = view(MaintenancePremiumSettlementStatus.NOT_STARTED);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1")).thenReturn(Optional.of(view));
        when(policyPort.getPolicyProductId("policy-1", "1")).thenReturn("product-1");
        when(productPort.calculateReplacement(any())).thenReturn(calculation());
        when(productPort.createAdjustment(any())).thenReturn(adjustment(MaintenanceBalanceDirection.DEBIT));
        when(billingPort.post(any())).thenReturn(posting(MaintenanceBalanceDirection.DEBIT));

        MaintenancePremiumSettlementResult result = orchestrator.settle("maintenance-1", "1", input());

        assertEquals(MaintenancePremiumSettlementStatus.POSTED.name(), result.premiumSettlementStatus());
        assertEquals("posting-1", result.billingPostingId());
        verify(commandGateway).sendAndWait(any(RecordMaintenancePremiumAdjustmentCommand.class));
        verify(commandGateway).sendAndWait(any(RecordMaintenancePremiumPostingCommand.class));
        verify(commandGateway).sendAndWait(any(RecordMaintenanceFinancialSettlementCommand.class));
    }

    @Test
    void shouldReverseExistingCreditAsDebitWithoutCreatingAnotherRefund() {
        MaintenanceView view = view(MaintenancePremiumSettlementStatus.NOT_STARTED);
        view.setMaintenanceType(MaintenanceType.POLICY_REVERSAL);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1"))
                .thenReturn(Optional.of(view));
        when(productPort.createReversal(any())).thenReturn(
                new ProductPremiumLifecyclePort.AdjustmentFact(
                        "reversal-1", "maintenance-1:reversal", "calc-replacement", "calc-original",
                        "reversal-hash", MaintenanceBalanceDirection.DEBIT, new BigDecimal("72.72"), "CNY"));
        when(billingPort.post(any())).thenReturn(new BillingPremiumLifecyclePort.PostingFact(
                "posting-reversal-1", "reversal-1", "reversal-hash", MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("72.72"), "CNY", "POSTED", null, null, "NOT_REQUIRED", 1));

        MaintenancePremiumSettlementResult result = orchestrator.settleReversal(
                "maintenance-1", "1", new MaintenanceReversalSettlementInput(
                        "adjustment-1", LocalDateTime.of(2026, 8, 21, 10, 0), "撤销退保贷项", "admin"));

        assertEquals(MaintenancePremiumSettlementStatus.POSTED.name(), result.premiumSettlementStatus());
        assertEquals(MaintenanceBalanceDirection.DEBIT.name(), result.direction());
        assertEquals(new BigDecimal("72.72"), result.amount());
        verify(productPort).createReversal(any());
        verify(billingPort).post(any());
        verify(commandGateway).sendAndWait(any(RecordMaintenancePremiumAdjustmentCommand.class));
    }

    @Test
    void shouldSkipBillingWhenProductHasNoBalanceImpact() {
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1"))
                .thenReturn(Optional.of(view(MaintenancePremiumSettlementStatus.NOT_STARTED)));
        when(policyPort.getPolicyProductId("policy-1", "1")).thenReturn("product-1");
        when(productPort.calculateReplacement(any())).thenReturn(calculation());
        when(productPort.createAdjustment(any())).thenReturn(adjustment(MaintenanceBalanceDirection.NONE));

        MaintenancePremiumSettlementResult result = orchestrator.settle("maintenance-1", "1", input());

        assertEquals(MaintenancePremiumSettlementStatus.NOT_REQUIRED.name(), result.premiumSettlementStatus());
        verify(billingPort, never()).post(any());
        verify(commandGateway, never()).sendAndWait(any(RecordMaintenancePremiumPostingCommand.class));
    }

    @Test
    void shouldResumeBillingFromProductCheckpointWithoutRecalculating() {
        MaintenanceView view = view(MaintenancePremiumSettlementStatus.ADJUSTMENT_CONFIRMED);
        view.setOriginalCalculationId("calc-original");
        view.setReplacementCalculationId("calc-replacement");
        view.setPremiumAdjustmentId("adjustment-1");
        view.setPremiumAdjustmentResultHash("adjustment-hash");
        view.setBalanceDirection(MaintenanceBalanceDirection.CREDIT);
        view.setBalanceAmount(new BigDecimal("53.00"));
        view.setBalanceCurrency("CNY");
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1")).thenReturn(Optional.of(view));
        when(billingPort.post(any())).thenReturn(posting(MaintenanceBalanceDirection.CREDIT));

        MaintenancePremiumSettlementResult result = orchestrator.settle("maintenance-1", "1", input());

        assertEquals("posting-1", result.billingPostingId());
        assertEquals(MaintenancePremiumSettlementStatus.SETTLEMENT_PENDING.name(),
                result.premiumSettlementStatus());
        verify(productPort, never()).calculateReplacement(any());
        verify(productPort, never()).createAdjustment(any());
    }

    @Test
    void shouldMapSucceededCreditRefundToSettled() {
        MaintenanceView view = adjustmentView(MaintenancePremiumSettlementStatus.ADJUSTMENT_CONFIRMED,
                MaintenanceBalanceDirection.CREDIT);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1")).thenReturn(Optional.of(view));
        when(billingPort.post(any())).thenReturn(posting(
                MaintenanceBalanceDirection.CREDIT, "refund-instruction-1", "refund-order-1", "SUCCEEDED"));

        MaintenancePremiumSettlementResult result = orchestrator.settle("maintenance-1", "1", input());

        assertEquals(MaintenancePremiumSettlementStatus.SETTLED.name(), result.premiumSettlementStatus());
        assertEquals("refund-order-1", result.refundOrderId());
    }

    @Test
    void shouldRetryBillingForEveryNonTerminalFinancialStatus() {
        for (MaintenancePremiumSettlementStatus status : List.of(
                MaintenancePremiumSettlementStatus.POSTED,
                MaintenancePremiumSettlementStatus.SETTLEMENT_PENDING,
                MaintenancePremiumSettlementStatus.SETTLEMENT_FAILED)) {
            MaintenanceView view = adjustmentView(status, MaintenanceBalanceDirection.CREDIT);
            when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1")).thenReturn(Optional.of(view));
            when(billingPort.post(any())).thenReturn(posting(MaintenanceBalanceDirection.CREDIT));

            orchestrator.settle("maintenance-1", "1", input());
        }

        verify(billingPort, times(3)).post(any());
        verify(productPort, never()).calculateReplacement(any());
    }

    @Test
    void shouldReturnTerminalSettlementWithoutCallingBilling() {
        MaintenanceView settled = adjustmentView(
                MaintenancePremiumSettlementStatus.SETTLED, MaintenanceBalanceDirection.CREDIT);
        settled.setBillingPostingId("posting-1");
        settled.setRefundInstructionId("refund-instruction-1");
        settled.setRefundOrderId("refund-order-1");
        settled.setRefundStatus("SUCCEEDED");
        settled.setCommissionAdjustmentCount(2);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1"))
                .thenReturn(Optional.of(settled));

        MaintenancePremiumSettlementResult result = orchestrator.settle("maintenance-1", "1", input());

        assertEquals(MaintenancePremiumSettlementStatus.SETTLED.name(), result.premiumSettlementStatus());
        assertEquals("SUCCEEDED", result.refundStatus());
        verify(billingPort, never()).post(any());
    }

    @Test
    void shouldRejectRefundOrderWithoutInstruction() {
        MaintenanceView view = adjustmentView(
                MaintenancePremiumSettlementStatus.ADJUSTMENT_CONFIRMED, MaintenanceBalanceDirection.CREDIT);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1")).thenReturn(Optional.of(view));
        when(billingPort.post(any())).thenReturn(posting(
                MaintenanceBalanceDirection.CREDIT, null, "refund-order-1", "PROCESSING"));

        assertThrows(com.titanium.maintenance.common.exception.BusinessException.class,
                () -> orchestrator.settle("maintenance-1", "1", input()));

        verify(commandGateway, never()).sendAndWait(any(RecordMaintenanceFinancialSettlementCommand.class));
    }

    @Test
    void shouldRejectPostingAmountThatDoesNotMatchProductAdjustment() {
        MaintenanceView view = adjustmentView(
                MaintenancePremiumSettlementStatus.ADJUSTMENT_CONFIRMED, MaintenanceBalanceDirection.CREDIT);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1")).thenReturn(Optional.of(view));
        BillingPremiumLifecyclePort.PostingFact posting = new BillingPremiumLifecyclePort.PostingFact(
                "posting-1", "adjustment-1", "adjustment-hash", MaintenanceBalanceDirection.CREDIT,
                new BigDecimal("52.00"), "CNY", "POSTED", "refund-instruction-1", "refund-order-1",
                "PROCESSING", 2);
        when(billingPort.post(any())).thenReturn(posting);

        assertThrows(com.titanium.maintenance.common.exception.BusinessException.class,
                () -> orchestrator.settle("maintenance-1", "1", input()));

        verify(commandGateway, never()).sendAndWait(any(RecordMaintenancePremiumPostingCommand.class));
    }

    @Test
    void shouldRejectIncompletePersistedAdjustmentBeforeCallingBilling() {
        MaintenanceView view = adjustmentView(
                MaintenancePremiumSettlementStatus.SETTLEMENT_PENDING, MaintenanceBalanceDirection.CREDIT);
        view.setBalanceCurrency(null);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1")).thenReturn(Optional.of(view));

        assertThrows(com.titanium.maintenance.common.exception.BusinessException.class,
                () -> orchestrator.settle("maintenance-1", "1", input()));

        verify(billingPort, never()).post(any());
    }

    @Test
    void shouldRejectCrossTenantCaseBeforeRemoteCalls() {
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "2")).thenReturn(Optional.empty());

        assertThrows(MaintenanceNotFoundException.class,
                () -> orchestrator.settle("maintenance-1", "2", input()));

        verify(productPort, never()).calculateReplacement(any());
        verify(billingPort, never()).post(any());
    }

    @Test
    void shouldRejectProductThatDoesNotBelongToPolicy() {
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "1"))
                .thenReturn(Optional.of(view(MaintenancePremiumSettlementStatus.NOT_STARTED)));
        when(policyPort.getPolicyProductId("policy-1", "1")).thenReturn("another-product");

        assertThrows(com.titanium.maintenance.common.exception.BusinessException.class,
                () -> orchestrator.settle("maintenance-1", "1", input()));

        verify(productPort, never()).calculateReplacement(any());
        verify(billingPort, never()).post(any());
    }

    private MaintenanceView view(MaintenancePremiumSettlementStatus status) {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("maintenance-1");
        view.setPolicyId("policy-1");
        view.setCustomerId("customer-1");
        view.setMaintenanceType(MaintenanceType.COVERAGE_AMOUNT_CHANGE);
        view.setPremiumSettlementStatus(status);
        view.setTenantId("1");
        return view;
    }

    private MaintenanceView adjustmentView(
            MaintenancePremiumSettlementStatus status, MaintenanceBalanceDirection direction) {
        MaintenanceView view = view(status);
        view.setOriginalCalculationId("calc-original");
        view.setReplacementCalculationId("calc-replacement");
        view.setPremiumAdjustmentId("adjustment-1");
        view.setPremiumAdjustmentResultHash("adjustment-hash");
        view.setBalanceDirection(direction);
        view.setBalanceAmount(new BigDecimal("53.00"));
        view.setBalanceCurrency("CNY");
        return view;
    }

    private MaintenancePremiumSettlementInput input() {
        return new MaintenancePremiumSettlementInput(
                "calc-original", "product-1", "V1.0", LocalDateTime.of(2026, 8, 20, 12, 0),
                "CNY", new BigDecimal("120000"), 35, "M", 1, 1, 1,
                Map.of("scenario", "D2-A"), List.of(), "channel-1", 1, "保额批改", "admin");
    }

    private ProductPremiumLifecyclePort.CalculationFact calculation() {
        return new ProductPremiumLifecyclePort.CalculationFact(
                "calc-replacement", "maintenance-1:replacement", "policy-1", "MAINTENANCE",
                "product-1", "V1.0", "CNY", "calculation-hash");
    }

    private ProductPremiumLifecyclePort.AdjustmentFact adjustment(MaintenanceBalanceDirection direction) {
        BigDecimal amount = direction == MaintenanceBalanceDirection.NONE
                ? BigDecimal.ZERO
                : new BigDecimal("53.00");
        return new ProductPremiumLifecyclePort.AdjustmentFact(
                "adjustment-1", "maintenance-1:adjustment", "calc-original", "calc-replacement",
                "adjustment-hash", direction, amount, "CNY");
    }

    private BillingPremiumLifecyclePort.PostingFact posting(MaintenanceBalanceDirection direction) {
        if (direction == MaintenanceBalanceDirection.DEBIT) {
            return posting(direction, null, null, "NOT_REQUIRED");
        }
        return posting(direction, "refund-instruction-1", "refund-order-1", "PROCESSING");
    }

    private BillingPremiumLifecyclePort.PostingFact posting(
            MaintenanceBalanceDirection direction,
            String refundInstructionId,
            String refundOrderId,
            String refundStatus) {
        return new BillingPremiumLifecyclePort.PostingFact(
                "posting-1", "adjustment-1", "adjustment-hash", direction,
                new BigDecimal("53.00"), "CNY", "POSTED", refundInstructionId, refundOrderId,
                refundStatus, 2);
    }

    private ProductSurrenderValuePort.SurrenderFact surrenderFact() {
        return new ProductSurrenderValuePort.SurrenderFact(
                "maintenance-1", "LIFE-SURRENDER-CASH-VALUE", "V1.0", hash('a'), 1, 15,
                "CASH_VALUE", false, new BigDecimal("0.60000000"), new BigDecimal("72.72"),
                new BigDecimal("48.48"), BigDecimal.ZERO, "calc-original", "calc-replacement",
                "adjustment-1", hash('b'), MaintenanceBalanceDirection.CREDIT,
                new BigDecimal("72.72"), "CNY");
    }

    private String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
