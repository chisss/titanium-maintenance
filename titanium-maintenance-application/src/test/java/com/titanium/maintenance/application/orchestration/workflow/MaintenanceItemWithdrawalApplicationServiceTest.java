package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.application.command.withdrawal.MaintenanceItemWithdrawalInput;
import com.titanium.maintenance.command.ConfigureMaintenanceItemWithdrawalRecoveryCommand;
import com.titanium.maintenance.command.FailMaintenanceItemWithdrawalCommand;
import com.titanium.maintenance.command.RecordMaintenanceItemWithdrawalCompensationCommand;
import com.titanium.maintenance.command.StartMaintenanceItemWithdrawalCommand;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalFundAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalStatus;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.port.billing.BillingPremiumLifecyclePort;
import com.titanium.maintenance.port.billing.BillingPremiumLifecyclePort.ReversalFact;
import com.titanium.maintenance.port.payment.PaymentMaintenanceRefundPort;
import com.titanium.maintenance.port.payment.PaymentMaintenanceRefundPort.RefundFact;
import com.titanium.maintenance.port.payment.PaymentPremiumCollectionPort;
import com.titanium.maintenance.port.payment.PaymentPremiumCollectionPort.CollectionFact;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawal;
import com.titanium.maintenance.valueobject.workflow.MaintenanceBillingPostingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceFundSettlementEvidence;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

class MaintenanceItemWithdrawalApplicationServiceTest {

    private static final String HASH = "a".repeat(64);
    private static final BigDecimal AMOUNT = new BigDecimal("20.00");

    private CommandGateway commandGateway;
    private MaintenanceViewRepository maintenanceRepository;
    private MaintenanceCaseItemViewRepository itemRepository;
    private MaintenanceWorkflowTaskViewRepository taskRepository;
    private BillingPremiumLifecyclePort billingPort;
    private PaymentPremiumCollectionPort collectionPort;
    private PaymentMaintenanceRefundPort refundPort;
    private MaintenanceItemWithdrawalApplicationService service;
    private MaintenanceWorkflowTaskView task;
    private MaintenanceItemWithdrawal aggregateWithdrawal;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        maintenanceRepository = mock(MaintenanceViewRepository.class);
        itemRepository = mock(MaintenanceCaseItemViewRepository.class);
        taskRepository = mock(MaintenanceWorkflowTaskViewRepository.class);
        billingPort = mock(BillingPremiumLifecyclePort.class);
        collectionPort = mock(PaymentPremiumCollectionPort.class);
        refundPort = mock(PaymentMaintenanceRefundPort.class);
        service = new MaintenanceItemWithdrawalApplicationService(
                commandGateway, maintenanceRepository, itemRepository, taskRepository,
                billingPort, collectionPort, refundPort);
        visibleContext();
        stubCommandGateway();
    }

    @Test
    void shouldReverseDebitAndRefundSuccessfulOriginalCollection() {
        financialTask(MaintenanceBalanceDirection.DEBIT,
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.SUCCEEDED, "payment-1");
        when(billingPort.reverse(any())).thenAnswer(invocation -> reversal(
                invocation.getArgument(0), MaintenanceBalanceDirection.CREDIT));
        when(refundPort.create(any())).thenAnswer(invocation -> {
            PaymentMaintenanceRefundPort.RefundRequest request = invocation.getArgument(0);
            return new RefundFact(
                    "refund-order-1", request.refundRequestId(), request.sourcePostingId(),
                    request.originalPaymentId(), request.amount(), request.currency(), "SUCCEEDED",
                    null, null, LocalDateTime.now());
        });

        var result = service.withdraw(input("BANK_CARD")).join();

        assertEquals(MaintenanceItemWithdrawalStatus.COMPLETED, result.status());
        assertEquals(MaintenanceBalanceDirection.CREDIT, result.reversalDirection());
        assertEquals(MaintenanceItemWithdrawalFundAction.REFUND, result.fundAction());
        verify(refundPort).create(any());
        verify(collectionPort, never()).create(any());
    }

    @Test
    void shouldReverseCreditAndCollectSuccessfulOriginalRefund() {
        financialTask(MaintenanceBalanceDirection.CREDIT,
                MaintenanceFundSettlementType.REFUND, MaintenanceFundSettlementStatus.SUCCEEDED, "refund-1");
        when(billingPort.reverse(any())).thenAnswer(invocation -> reversal(
                invocation.getArgument(0), MaintenanceBalanceDirection.DEBIT));
        when(collectionPort.create(any())).thenAnswer(invocation -> {
            PaymentPremiumCollectionPort.CollectionRequest request = invocation.getArgument(0);
            return new CollectionFact(
                    request.paymentOrderId(), request.policyId(), request.customerId(), request.amount(),
                    request.currency(), request.paymentMethod(), "SUCCESS", "transaction-1", LocalDateTime.now());
        });

        var result = service.withdraw(input("BANK_CARD")).join();

        assertEquals(MaintenanceItemWithdrawalStatus.COMPLETED, result.status());
        assertEquals(MaintenanceBalanceDirection.DEBIT, result.reversalDirection());
        assertEquals(MaintenanceItemWithdrawalFundAction.COLLECTION, result.fundAction());
        verify(collectionPort).create(any());
        verify(refundPort, never()).create(any());
    }

    @Test
    void shouldWaitForOriginalCollectionBeforeBillingReversal() {
        financialTask(MaintenanceBalanceDirection.DEBIT,
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.PROCESSING, "payment-1");
        when(collectionPort.get("tenant-1", "payment-1")).thenReturn(new CollectionFact(
                "payment-1", "policy-1", "customer-1", AMOUNT, "CNY",
                "BANK_CARD", "PROCESSING", null, null));

        var result = service.withdraw(input("BANK_CARD")).join();

        assertEquals(MaintenanceItemWithdrawalStatus.WAITING_FUNDS, result.status());
        assertEquals(MaintenanceFundSettlementStatus.PROCESSING, result.fundStatus());
        verify(billingPort, never()).reverse(any());
    }

    @Test
    void shouldCompleteWithoutExternalCallsWhenItemHasNoPosting() {
        task = null;
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "case-1")).thenReturn(List.of());

        var result = service.withdraw(input(null)).join();

        assertEquals(MaintenanceItemWithdrawalStatus.COMPLETED, result.status());
        assertEquals(MaintenanceItemWithdrawalFundAction.NOT_REQUIRED, result.fundAction());
        verify(billingPort, never()).reverse(any());
        verify(collectionPort, never()).create(any());
        verify(refundPort, never()).create(any());
    }

    @Test
    void shouldRecordRecoverableFailureWhenBillingIsUnavailable() {
        financialTask(MaintenanceBalanceDirection.DEBIT,
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.SUCCEEDED, "payment-1");
        when(billingPort.reverse(any())).thenThrow(new MaintenanceRemoteCallException(
                "Billing unavailable", MaintenanceErrorCode.MAINTENANCE_BILLING_REMOTE_ERROR));

        var result = service.withdraw(input("BANK_CARD")).join();

        assertEquals(MaintenanceItemWithdrawalStatus.FAILED, result.status());
        assertEquals(MaintenanceErrorCode.MAINTENANCE_BILLING_REMOTE_ERROR.getCode(), result.failureCode());
        verify(commandGateway).send(any(FailMaintenanceItemWithdrawalCommand.class));
    }

    @Test
    void shouldPreserveReversalEvidenceWhenPaymentIsUnavailable() {
        financialTask(MaintenanceBalanceDirection.DEBIT,
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.SUCCEEDED, "payment-1");
        when(billingPort.reverse(any())).thenAnswer(invocation -> reversal(
                invocation.getArgument(0), MaintenanceBalanceDirection.CREDIT));
        when(refundPort.create(any())).thenThrow(new MaintenanceRemoteCallException(
                "Payment unavailable", MaintenanceErrorCode.MAINTENANCE_PAYMENT_REFUND_REMOTE_ERROR));

        var result = service.withdraw(input("BANK_CARD")).join();

        assertEquals(MaintenanceItemWithdrawalStatus.FAILED, result.status());
        assertEquals("reversal-1", result.reversalId());
        assertEquals(MaintenanceErrorCode.MAINTENANCE_PAYMENT_REFUND_REMOTE_ERROR.getCode(), result.failureCode());
        verify(commandGateway).send(any(RecordMaintenanceItemWithdrawalCompensationCommand.class));
        verify(commandGateway, never()).send(any(FailMaintenanceItemWithdrawalCommand.class));
    }

    private void visibleContext() {
        MaintenanceView caseView = new MaintenanceView();
        caseView.setPolicyId("policy-1");
        caseView.setCustomerId("customer-1");
        MaintenanceCaseItemView itemView = new MaintenanceCaseItemView();
        itemView.setItemCode("ITEM_A");
        when(maintenanceRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "case-1", "tenant-1"))
                .thenReturn(Optional.of(caseView));
        when(itemRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                "tenant-1", "case-1", "ITEM_A"))
                .thenReturn(Optional.of(itemView));
    }

    private void financialTask(
            MaintenanceBalanceDirection direction,
            MaintenanceFundSettlementType fundType,
            MaintenanceFundSettlementStatus fundStatus,
            String fundOrderId) {
        task = new MaintenanceWorkflowTaskView();
        task.setTaskId("fee-task-1");
        task.setItemCode("ITEM_A");
        task.setStepType(MaintenanceStepType.FEE_SETTLEMENT);
        task.setBillingPostingId("posting-1");
        task.setBillingAdjustmentId("adjustment-1");
        task.setBillingResultHash(HASH);
        task.setBillingPostingDirection(direction);
        task.setBillingPostingAmount(AMOUNT);
        task.setBillingPostingCurrency("CNY");
        task.setBillingPostingStatus(MaintenanceBillingPostingStatus.POSTED);
        task.setFundSettlementType(fundType);
        task.setFundSettlementStatus(fundStatus);
        task.setFundSettlementOrderId(fundOrderId);
        task.setFundSettlementExternalStatus(fundStatus.getCode());
        when(taskRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "case-1")).thenReturn(List.of(task));
    }

    private void stubCommandGateway() {
        when(commandGateway.send(any())).thenAnswer(invocation -> {
            Object command = invocation.getArgument(0);
            if (command instanceof StartMaintenanceItemWithdrawalCommand start) {
                aggregateWithdrawal = MaintenanceItemWithdrawal.requested(
                        start.itemCode(), start.operationId(), start.requestHash(), start.reason(),
                        sourcePosting(), sourceFunds(), LocalDateTime.now(), start.operatorId());
            } else if (command instanceof ConfigureMaintenanceItemWithdrawalRecoveryCommand) {
                return CompletableFuture.completedFuture(null);
            } else if (command instanceof RecordMaintenanceItemWithdrawalCompensationCommand record) {
                aggregateWithdrawal = aggregateWithdrawal.recordCompensation(record.compensation());
            } else if (command instanceof FailMaintenanceItemWithdrawalCommand fail) {
                aggregateWithdrawal = aggregateWithdrawal.fail(
                        fail.failureCode(), fail.failureMessage(), LocalDateTime.now());
            }
            return CompletableFuture.completedFuture(aggregateWithdrawal);
        });
    }

    private MaintenanceBillingPostingEvidence sourcePosting() {
        return task == null ? null : new MaintenanceBillingPostingEvidence(
                task.getBillingPostingId(), task.getBillingAdjustmentId(), task.getBillingResultHash(),
                task.getBillingPostingDirection(), task.getBillingPostingAmount(), task.getBillingPostingCurrency(),
                task.getBillingPostingStatus(), 0, LocalDateTime.now());
    }

    private MaintenanceFundSettlementEvidence sourceFunds() {
        if (task == null) {
            return null;
        }
        return new MaintenanceFundSettlementEvidence(
                task.getFundSettlementType(), task.getFundSettlementStatus(), task.getBillingPostingId(),
                task.getFundSettlementType() == MaintenanceFundSettlementType.REFUND ? "instruction-1" : null,
                task.getFundSettlementOrderId(), task.getFundSettlementExternalStatus(),
                task.getBillingPostingAmount(), task.getBillingPostingCurrency(), null, null, LocalDateTime.now());
    }

    private ReversalFact reversal(
            BillingPremiumLifecyclePort.ReversalRequest request,
            MaintenanceBalanceDirection direction) {
        return new ReversalFact(
                "reversal-1", request.requestId(), "b".repeat(64), "c".repeat(64),
                request.sourcePostingId(), HASH, "policy-1", "customer-1", direction,
                AMOUNT, "CNY", "REVERSED", LocalDateTime.now());
    }

    private MaintenanceItemWithdrawalInput input(String paymentMethod) {
        return new MaintenanceItemWithdrawalInput(
                "case-1", "ITEM_A", "withdraw-operation-1", "客户取消项目",
                paymentMethod, "operator-1", "tenant-1");
    }
}
