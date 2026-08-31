package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.MaintenancePremiumSettlementGateInput;
import com.titanium.maintenance.command.FailMaintenancePremiumSettlementCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumSettlementCommand;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort.PostingFact;
import com.titanium.maintenance.port.PaymentPremiumCollectionPort;
import com.titanium.maintenance.port.PaymentPremiumCollectionPort.CollectionFact;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

class MaintenancePremiumSettlementApplicationServiceTest {

    private static final String RESULT_HASH = "f".repeat(64);

    private CommandGateway commandGateway;
    private MaintenanceViewRepository maintenanceRepository;
    private MaintenanceWorkflowTaskViewRepository taskRepository;
    private BillingPremiumLifecyclePort billingPort;
    private PaymentPremiumCollectionPort paymentPort;
    private MaintenancePremiumSettlementApplicationService service;
    private MaintenanceWorkflowTaskView task;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        maintenanceRepository = mock(MaintenanceViewRepository.class);
        taskRepository = mock(MaintenanceWorkflowTaskViewRepository.class);
        billingPort = mock(BillingPremiumLifecyclePort.class);
        paymentPort = mock(PaymentPremiumCollectionPort.class);
        service = new MaintenancePremiumSettlementApplicationService(
                commandGateway, maintenanceRepository, taskRepository, billingPort, paymentPort);
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        visibleContext(MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"));
    }

    @Test
    void shouldCreateDebitCollectionAndWaitForPayment() {
        when(billingPort.post(any())).thenReturn(posting(
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "POSTED",
                null, null, "NOT_REQUIRED"));
        when(paymentPort.create(any())).thenAnswer(invocation -> {
            PaymentPremiumCollectionPort.CollectionRequest request = invocation.getArgument(0);
            return new CollectionFact(
                    request.paymentOrderId(), request.policyId(), request.customerId(), request.amount(),
                    request.currency(), request.paymentMethod(), "PENDING", null, null);
        });

        var result = service.settle(input()).join();

        assertEquals(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL, result.taskStatus());
        assertEquals(MaintenanceFundSettlementStatus.PENDING, result.fundStatus());
        ArgumentCaptor<RecordMaintenancePremiumSettlementCommand> captor =
                ArgumentCaptor.forClass(RecordMaintenancePremiumSettlementCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals(MaintenanceFundSettlementType.COLLECTION,
                captor.getValue().fundEvidence().type());
    }

    @Test
    void shouldRefreshExistingCollectionAndCompleteAfterSuccess() {
        existingPendingCollection();
        when(billingPort.post(any())).thenReturn(posting(
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "POSTED",
                null, null, "NOT_REQUIRED"));
        when(paymentPort.get("tenant-1", "payment-1")).thenReturn(collection("SUCCESS"));

        var result = service.settle(input()).join();
        service.settle(input()).join();

        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, result.taskStatus());
        assertEquals(MaintenanceFundSettlementStatus.SUCCEEDED, result.fundStatus());
        verify(paymentPort, never()).create(any());
        ArgumentCaptor<RecordMaintenancePremiumSettlementCommand> command =
                ArgumentCaptor.forClass(RecordMaintenancePremiumSettlementCommand.class);
        verify(commandGateway, times(2)).send(command.capture());
        String firstOperationId = command.getAllValues().get(0).operationId();
        String secondOperationId = command.getAllValues().get(1).operationId();
        assertTrue(firstOperationId.startsWith("settlement-"));
        assertNotEquals("operation-1", firstOperationId);
        assertEquals(firstOperationId, secondOperationId);
    }

    @Test
    void shouldReturnExistingCheckpointWhenExternalCollectionStatusIsUnchanged() {
        existingPendingCollection();
        when(billingPort.post(any())).thenReturn(posting(
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "POSTED",
                null, null, "NOT_REQUIRED"));
        when(paymentPort.get("tenant-1", "payment-1")).thenReturn(collection("PENDING"));

        var result = service.settle(input()).join();

        assertEquals(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL, result.taskStatus());
        assertEquals(MaintenanceFundSettlementStatus.PENDING, result.fundStatus());
        verify(commandGateway, never()).send(any(RecordMaintenancePremiumSettlementCommand.class));
    }

    @Test
    void shouldCompleteCreditOnlyAfterIndependentRefundSucceeds() {
        visibleContext(MaintenanceBalanceDirection.CREDIT, new BigDecimal("15"));
        when(billingPort.post(any())).thenReturn(posting(
                MaintenanceBalanceDirection.CREDIT, new BigDecimal("15"), "POSTED",
                "instruction-1", "refund-1", "SUCCEEDED"));

        var result = service.settle(input()).join();

        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, result.taskStatus());
        assertEquals(MaintenanceFundSettlementType.REFUND, result.fundType());
        assertEquals("refund-1", result.orderId());
        verify(paymentPort, never()).create(any());
    }

    @Test
    void shouldFailCreditWhenRefundAllocationIsMissing() {
        visibleContext(MaintenanceBalanceDirection.CREDIT, new BigDecimal("15"));
        when(billingPort.post(any())).thenReturn(posting(
                MaintenanceBalanceDirection.CREDIT, new BigDecimal("15"), "POSTED",
                null, null, "ALLOCATION_REQUIRED"));

        var result = service.settle(input()).join();

        assertEquals(MaintenanceWorkflowTaskStatus.FAILED, result.taskStatus());
        assertEquals("PAYMENT_REFUND_ALLOCATION_REQUIRED", result.failureCode());
        verify(paymentPort, never()).create(any());
    }

    @Test
    void shouldCompleteNoDifferenceWithoutCallingPayment() {
        visibleContext(MaintenanceBalanceDirection.NONE, BigDecimal.ZERO);
        when(billingPort.post(any())).thenReturn(posting(
                MaintenanceBalanceDirection.NONE, BigDecimal.ZERO, "POSTED",
                null, null, "NOT_REQUIRED"));

        var result = service.settle(input()).join();

        assertEquals(MaintenanceWorkflowTaskStatus.COMPLETED, result.taskStatus());
        assertEquals(MaintenanceFundSettlementStatus.NOT_REQUIRED, result.fundStatus());
        verify(paymentPort, never()).create(any());
    }

    @Test
    void shouldKeepPostingEvidenceWhenPaymentIsUnavailable() {
        when(billingPort.post(any())).thenReturn(posting(
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20"), "POSTED",
                null, null, "NOT_REQUIRED"));
        when(paymentPort.create(any())).thenThrow(new MaintenanceRemoteCallException(
                "Payment unavailable", MaintenanceErrorCode.MAINTENANCE_PAYMENT_REMOTE_ERROR));

        var result = service.settle(input()).join();

        assertEquals(MaintenanceWorkflowTaskStatus.FAILED, result.taskStatus());
        assertEquals("posting-1", result.postingId());
        ArgumentCaptor<RecordMaintenancePremiumSettlementCommand> captor =
                ArgumentCaptor.forClass(RecordMaintenancePremiumSettlementCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals(MaintenanceErrorCode.MAINTENANCE_PAYMENT_REMOTE_ERROR.getCode(),
                captor.getValue().fundEvidence().failureCode());
    }

    @Test
    void shouldRecordRecoverableFailureWhenBillingIsUnavailable() {
        when(billingPort.post(any())).thenThrow(new MaintenanceRemoteCallException(
                "Billing unavailable", MaintenanceErrorCode.MAINTENANCE_BILLING_REMOTE_ERROR));

        var result = service.settle(input()).join();

        assertEquals(MaintenanceWorkflowTaskStatus.FAILED, result.taskStatus());
        ArgumentCaptor<FailMaintenancePremiumSettlementCommand> captor =
                ArgumentCaptor.forClass(FailMaintenancePremiumSettlementCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals(MaintenanceErrorCode.MAINTENANCE_BILLING_REMOTE_ERROR.getCode(),
                captor.getValue().failureCode());
    }

    private void visibleContext(MaintenanceBalanceDirection direction, BigDecimal amount) {
        MaintenanceView caseView = new MaintenanceView();
        caseView.setMaintenanceId("case-1");
        caseView.setTenantId("tenant-1");
        caseView.setPolicyId("policy-1");
        caseView.setCustomerId("customer-1");
        when(maintenanceRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "case-1", "tenant-1"))
                .thenReturn(Optional.of(caseView));

        task = new MaintenanceWorkflowTaskView();
        task.setTaskId("fee-task");
        task.setStepType(MaintenanceStepType.FEE_SETTLEMENT);
        task.setStatus(MaintenanceWorkflowTaskStatus.QUOTED);
        task.setPremiumQuoteStatus(MaintenancePremiumQuoteStatus.QUOTED);
        task.setPremiumQuoteId("adjustment-1");
        task.setPremiumQuoteResultHash(RESULT_HASH);
        task.setPremiumQuoteDirection(direction);
        task.setPremiumQuoteAmount(amount);
        task.setPremiumQuoteCurrency("CNY");
        task.setPremiumQuoteValidUntil(LocalDateTime.now().plusHours(1));
        when(taskRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", "case-1", "fee-task"))
                .thenReturn(Optional.of(task));
    }

    private MaintenancePremiumSettlementGateInput input() {
        return new MaintenancePremiumSettlementGateInput(
                "case-1", "fee-task", "operation-1", "BANK", "保全追加保费",
                "operator-1", "tenant-1", MaintenanceChannel.API);
    }

    private void existingPendingCollection() {
        task.setStatus(MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL);
        task.setBillingPostingId("posting-1");
        task.setBillingAdjustmentId("adjustment-1");
        task.setBillingResultHash(RESULT_HASH);
        task.setBillingPostingDirection(MaintenanceBalanceDirection.DEBIT);
        task.setBillingPostingAmount(new BigDecimal("20"));
        task.setBillingPostingCurrency("CNY");
        task.setBillingPostingStatus(MaintenanceBillingPostingStatus.POSTED);
        task.setFundSettlementType(MaintenanceFundSettlementType.COLLECTION);
        task.setFundSettlementStatus(MaintenanceFundSettlementStatus.PENDING);
        task.setFundSourcePostingId("posting-1");
        task.setFundSettlementOrderId("payment-1");
        task.setFundSettlementExternalStatus("PENDING");
        task.setFundSettlementAmount(new BigDecimal("20"));
        task.setFundSettlementCurrency("CNY");
        task.setFundSettlementRecordedAt(LocalDateTime.now());
    }

    private PostingFact posting(
            MaintenanceBalanceDirection direction,
            BigDecimal amount,
            String status,
            String instructionId,
            String refundOrderId,
            String refundStatus) {
        return new PostingFact(
                "posting-1", "adjustment-1", RESULT_HASH, direction, amount, "CNY",
                status, instructionId, refundOrderId, refundStatus, 0);
    }

    private CollectionFact collection(String status) {
        return new CollectionFact(
                "payment-1", "policy-1", "customer-1", new BigDecimal("20"),
                "CNY", "BANK", status, null, null);
    }
}
