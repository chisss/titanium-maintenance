package com.titanium.maintenance.application.orchestration.workflow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.premium.MaintenancePremiumSettlementGateInput;
import com.titanium.maintenance.application.model.premium.MaintenancePremiumSettlementGateResult;
import com.titanium.maintenance.command.FailMaintenancePremiumSettlementCommand;
import com.titanium.maintenance.command.RecordMaintenancePremiumSettlementCommand;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.billing.BillingPremiumLifecyclePort;
import com.titanium.maintenance.port.billing.BillingPremiumLifecyclePort.PostingFact;
import com.titanium.maintenance.port.billing.BillingPremiumLifecyclePort.PostingRequest;
import com.titanium.maintenance.port.payment.PaymentPremiumCollectionPort;
import com.titanium.maintenance.port.payment.PaymentPremiumCollectionPort.CollectionFact;
import com.titanium.maintenance.port.payment.PaymentPremiumCollectionPort.CollectionRequest;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceBillingPostingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceFundSettlementEvidence;

import lombok.RequiredArgsConstructor;

/** 独立案件费用任务的 Billing 入账与 Payment 资金编排。 */
@Service
@RequiredArgsConstructor
public class MaintenancePremiumSettlementApplicationService {

    private static final String BILLING_REVERSED = "BILLING_POSTING_REVERSED";

    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceWorkflowTaskViewRepository workflowTaskViewRepository;
    private final BillingPremiumLifecyclePort billingPort;
    private final PaymentPremiumCollectionPort paymentPort;

    public CompletableFuture<MaintenancePremiumSettlementGateResult> settle(
            MaintenancePremiumSettlementGateInput input) {
        SettlementContext context = requireContext(input);
        if (context.task().getPremiumQuoteStatus() == MaintenancePremiumQuoteStatus.NOT_REQUIRED) {
            return CompletableFuture.completedFuture(result(context.task()));
        }
        if (context.task().getStatus() == MaintenanceWorkflowTaskStatus.COMPLETED) {
            return CompletableFuture.completedFuture(result(context.task()));
        }
        requireSettlementReady(context.task());
        try {
            return settleAgainstExternalSystems(input, context);
        } catch (BusinessException exception) {
            return recordExternalFailure(input, exception);
        }
    }

    private CompletableFuture<MaintenancePremiumSettlementGateResult> settleAgainstExternalSystems(
            MaintenancePremiumSettlementGateInput input,
            SettlementContext context) {
        PostingFact postingFact = billingPort.post(new PostingRequest(
                input.tenantId(), context.task().getPremiumQuoteId(),
                context.task().getPremiumQuoteResultHash(), context.caseView().getPolicyId(),
                context.caseView().getCustomerId(), input.operatorId()));
        MaintenanceBillingPostingEvidence posting = postingEvidence(context, postingFact);
        validateExistingPosting(context.task(), posting);
        MaintenanceFundSettlementEvidence funds = resolveFunds(
                input, context, postingFact, posting);
        if (sameSettlementCheckpoint(context.task(), posting, funds)) {
            return CompletableFuture.completedFuture(result(context.task()));
        }
        String operationId = context.task().getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL
                ? checkpointOperationId(input.operationId(), posting, funds)
                : input.operationId();
        RecordMaintenancePremiumSettlementCommand command =
                new RecordMaintenancePremiumSettlementCommand(
                        MaintenanceId.of(input.maintenanceId()), input.taskId(), operationId,
                        posting, funds, input.operatorId());
        return send(command).thenApply(ignored -> result(posting, funds));
    }

    private void validateExistingPosting(
            MaintenanceWorkflowTaskView task,
            MaintenanceBillingPostingEvidence posting) {
        if (task.getBillingPostingId() == null) {
            return;
        }
        if (!task.getBillingPostingId().equals(posting.postingId())
                || !Objects.equals(task.getBillingAdjustmentId(), posting.adjustmentId())
                || !Objects.equals(task.getBillingResultHash(), posting.resultHash())
                || task.getBillingPostingDirection() != posting.direction()
                || task.getBillingPostingAmount() == null
                || task.getBillingPostingAmount().compareTo(posting.amount()) != 0
                || !task.getBillingPostingCurrency().equalsIgnoreCase(posting.currency())) {
            throw validation("billingPosting", "结算恢复时 Billing 原入账事实发生漂移");
        }
    }

    private boolean sameSettlementCheckpoint(
            MaintenanceWorkflowTaskView task,
            MaintenanceBillingPostingEvidence posting,
            MaintenanceFundSettlementEvidence funds) {
        return task.getStatus() == MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL
                && Objects.equals(task.getBillingPostingId(), posting.postingId())
                && task.getFundSettlementType() == funds.type()
                && task.getFundSettlementStatus() == funds.status()
                && Objects.equals(task.getFundSourcePostingId(), funds.sourcePostingId())
                && Objects.equals(task.getFundSettlementInstructionId(), funds.instructionId())
                && Objects.equals(task.getFundSettlementOrderId(), funds.orderId())
                && Objects.equals(task.getFundSettlementExternalStatus(), funds.externalStatus())
                && sameAmount(task.getFundSettlementAmount(), funds.amount())
                && equalsIgnoreCase(task.getFundSettlementCurrency(), funds.currency())
                && Objects.equals(task.getFundSettlementFailureCode(), funds.failureCode())
                && Objects.equals(task.getFundSettlementFailureMessage(), funds.failureMessage());
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String checkpointOperationId(
            String operationId,
            MaintenanceBillingPostingEvidence posting,
            MaintenanceFundSettlementEvidence funds) {
        String material = String.join(":", operationId, posting.postingId(), funds.type().getCode(),
                funds.status().getCode(), funds.externalStatus());
        return "settlement-" + UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    private MaintenanceFundSettlementEvidence resolveFunds(
            MaintenancePremiumSettlementGateInput input,
            SettlementContext context,
            PostingFact postingFact,
            MaintenanceBillingPostingEvidence posting) {
        try {
            return fundEvidence(input, context, postingFact, posting);
        } catch (BusinessException exception) {
            return paymentFailureFunds(context, posting, exception);
        }
    }

    private MaintenanceBillingPostingEvidence postingEvidence(
            SettlementContext context,
            PostingFact fact) {
        MaintenanceBillingPostingStatus status = fact == null
                ? null
                : MaintenanceBillingPostingStatus.fromCode(fact.status());
        if (fact == null || blank(fact.postingId()) || blank(fact.adjustmentId())
                || blank(fact.resultHash()) || fact.direction() == null || fact.amount() == null
                || blank(fact.currency()) || status == null || fact.commissionAdjustmentCount() == null
                || !Objects.equals(context.task().getPremiumQuoteId(), fact.adjustmentId())
                || !Objects.equals(context.task().getPremiumQuoteResultHash(), fact.resultHash())
                || context.task().getPremiumQuoteDirection() != fact.direction()
                || context.task().getPremiumQuoteAmount().compareTo(fact.amount()) != 0
                || !context.task().getPremiumQuoteCurrency().equalsIgnoreCase(fact.currency())) {
            throw validation("billingPosting", "Billing 入账结果未通过 Product 报价勾稽");
        }
        return new MaintenanceBillingPostingEvidence(
                fact.postingId(), fact.adjustmentId(), fact.resultHash(), fact.direction(),
                fact.amount(), fact.currency(), status, fact.commissionAdjustmentCount(),
                LocalDateTime.now());
    }

    private MaintenanceFundSettlementEvidence fundEvidence(
            MaintenancePremiumSettlementGateInput input,
            SettlementContext context,
            PostingFact fact,
            MaintenanceBillingPostingEvidence posting) {
        if (posting.status() == MaintenanceBillingPostingStatus.REVERSED) {
            return reversedFunds(posting);
        }
        return switch (posting.direction()) {
            case NONE -> notRequiredFunds(posting);
            case DEBIT -> collectionFunds(input, context, posting);
            case CREDIT -> refundFunds(fact, posting);
        };
    }

    private MaintenanceFundSettlementEvidence collectionFunds(
            MaintenancePremiumSettlementGateInput input,
            SettlementContext context,
            MaintenanceBillingPostingEvidence posting) {
        CollectionFact fact;
        String expectedPaymentOrderId;
        boolean refreshExisting = context.task().getFundSettlementType()
                        == MaintenanceFundSettlementType.COLLECTION
                && context.task().getFundSettlementOrderId() != null
                && context.task().getFundSettlementStatus() != MaintenanceFundSettlementStatus.FAILED
                && context.task().getFundSettlementStatus() != MaintenanceFundSettlementStatus.REVERSED;
        if (refreshExisting) {
            expectedPaymentOrderId = context.task().getFundSettlementOrderId();
            fact = paymentPort.get(input.tenantId(), expectedPaymentOrderId);
        } else {
            if (blank(input.paymentMethod())) {
                throw validation("paymentMethod", "创建追加保费收款单必须指定支付渠道");
            }
            expectedPaymentOrderId = paymentOrderId(
                    input.tenantId(), posting.postingId(), context.task().getRetryCount());
            fact = paymentPort.create(new CollectionRequest(
                    input.tenantId(), expectedPaymentOrderId,
                    context.caseView().getPolicyId(), context.caseView().getCustomerId(),
                    posting.amount(), posting.currency(), input.paymentMethod(), input.reason()));
        }
        validateCollectionFact(context, posting, expectedPaymentOrderId, fact);
        MaintenanceFundSettlementStatus status = collectionStatus(fact.status());
        String failureCode = status.failed() ? "PAYMENT_COLLECTION_" + fact.status() : null;
        String failureMessage = status.failed() ? "Payment 收款单状态为 " + fact.status() : null;
        return new MaintenanceFundSettlementEvidence(
                MaintenanceFundSettlementType.COLLECTION, status, posting.postingId(), null,
                fact.paymentOrderId(), fact.status(), fact.amount(), fact.currency(),
                failureCode, failureMessage, LocalDateTime.now());
    }

    private void validateCollectionFact(
            SettlementContext context,
            MaintenanceBillingPostingEvidence posting,
            String expectedPaymentOrderId,
            CollectionFact fact) {
        if (fact == null || blank(fact.paymentOrderId()) || blank(fact.status())
                || fact.amount() == null || blank(fact.currency())
                || !Objects.equals(expectedPaymentOrderId, fact.paymentOrderId())
                || !Objects.equals(context.caseView().getPolicyId(), fact.policyId())
                || !Objects.equals(context.caseView().getCustomerId(), fact.customerId())
                || posting.amount().compareTo(fact.amount()) != 0
                || !posting.currency().equalsIgnoreCase(fact.currency())) {
            throw validation("paymentCollection", "Payment 收款单未通过案件与 Billing 入账勾稽");
        }
    }

    private MaintenanceFundSettlementEvidence refundFunds(
            PostingFact fact,
            MaintenanceBillingPostingEvidence posting) {
        MaintenanceFundSettlementStatus status = refundStatus(fact.refundStatus());
        if (!status.failed() && (blank(fact.refundInstructionId()) || blank(fact.refundOrderId()))) {
            throw validation("refundEvidence", "Payment 退款处理中或成功时必须携带指令和退款单号");
        }
        String failureCode = status.failed() ? "PAYMENT_REFUND_" + normalizeCode(fact.refundStatus()) : null;
        String failureMessage = status.failed() ? "Payment 退款状态为 " + fact.refundStatus() : null;
        return new MaintenanceFundSettlementEvidence(
                MaintenanceFundSettlementType.REFUND, status, posting.postingId(),
                fact.refundInstructionId(), fact.refundOrderId(), fact.refundStatus(),
                posting.amount(), posting.currency(), failureCode, failureMessage, LocalDateTime.now());
    }

    private MaintenanceFundSettlementEvidence notRequiredFunds(
            MaintenanceBillingPostingEvidence posting) {
        return new MaintenanceFundSettlementEvidence(
                MaintenanceFundSettlementType.NOT_REQUIRED,
                MaintenanceFundSettlementStatus.NOT_REQUIRED, posting.postingId(), null, null,
                MaintenanceFundSettlementStatus.NOT_REQUIRED.getCode(), BigDecimal.ZERO,
                posting.currency(), null, null, LocalDateTime.now());
    }

    private MaintenanceFundSettlementEvidence reversedFunds(
            MaintenanceBillingPostingEvidence posting) {
        if (posting.direction() == MaintenanceBalanceDirection.NONE) {
            return notRequiredFunds(posting);
        }
        MaintenanceFundSettlementType type = posting.direction() == MaintenanceBalanceDirection.DEBIT
                ? MaintenanceFundSettlementType.COLLECTION
                : MaintenanceFundSettlementType.REFUND;
        return new MaintenanceFundSettlementEvidence(
                type, MaintenanceFundSettlementStatus.REVERSED, posting.postingId(), null, null,
                MaintenanceBillingPostingStatus.REVERSED.getCode(), posting.amount(), posting.currency(),
                BILLING_REVERSED, "Billing 入账已冲正", LocalDateTime.now());
    }

    private MaintenanceFundSettlementEvidence paymentFailureFunds(
            SettlementContext context,
            MaintenanceBillingPostingEvidence posting,
            BusinessException exception) {
        if (posting.direction() != MaintenanceBalanceDirection.DEBIT) {
            throw exception;
        }
        return new MaintenanceFundSettlementEvidence(
                MaintenanceFundSettlementType.COLLECTION, MaintenanceFundSettlementStatus.FAILED,
                posting.postingId(), null, context.task().getFundSettlementOrderId(),
                MaintenanceFundSettlementStatus.FAILED.getCode(), posting.amount(), posting.currency(),
                exception.getErrorCode(), safeReason(exception.getMessage()), LocalDateTime.now());
    }

    private SettlementContext requireContext(MaintenancePremiumSettlementGateInput input) {
        if (input == null || blank(input.maintenanceId()) || blank(input.taskId())
                || blank(input.operationId()) || blank(input.reason()) || blank(input.operatorId())
                || blank(input.tenantId()) || input.source() == null) {
            throw validation("settlementInput", "结算操作上下文不完整");
        }
        MaintenanceView caseView = maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        MaintenanceWorkflowTaskView task = workflowTaskViewRepository
                .findByTenantIdAndMaintenanceIdAndTaskId(
                        input.tenantId(), input.maintenanceId(), input.taskId())
                .orElseThrow(MaintenanceNotFoundException::new);
        if (task.getStepType() != MaintenanceStepType.FEE_SETTLEMENT) {
            throw validation("taskId", "目标任务不是收退费步骤");
        }
        return new SettlementContext(caseView, task);
    }

    private void requireSettlementReady(MaintenanceWorkflowTaskView task) {
        if (task.getPremiumQuoteStatus() != MaintenancePremiumQuoteStatus.QUOTED
                || task.getPremiumQuoteId() == null || task.getPremiumQuoteResultHash() == null
                || task.getPremiumQuoteDirection() == null || task.getPremiumQuoteAmount() == null
                || task.getPremiumQuoteCurrency() == null) {
            throw validation("premiumQuote", "费用任务必须先取得完整 Product 报价");
        }
        if (task.getBillingPostingId() == null
                && task.getPremiumQuoteValidUntil() != null
                && !LocalDateTime.now().isBefore(task.getPremiumQuoteValidUntil())) {
            throw validation("premiumQuote", "Product 报价已过期，必须重新报价");
        }
        if (task.getStatus() == MaintenanceWorkflowTaskStatus.FAILED) {
            throw validation("taskStatus", "失败费用任务必须先执行 retry 再重新结算");
        }
        if (task.getStatus() != MaintenanceWorkflowTaskStatus.QUOTED
                && task.getStatus() != MaintenanceWorkflowTaskStatus.READY
                && task.getStatus() != MaintenanceWorkflowTaskStatus.IN_PROGRESS
                && task.getStatus() != MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL) {
            throw validation("taskStatus", "当前费用任务状态不允许执行结算: " + task.getStatus());
        }
    }

    private CompletableFuture<MaintenancePremiumSettlementGateResult> recordExternalFailure(
            MaintenancePremiumSettlementGateInput input,
            BusinessException exception) {
        String reason = safeReason(exception.getMessage());
        FailMaintenancePremiumSettlementCommand command = new FailMaintenancePremiumSettlementCommand(
                MaintenanceId.of(input.maintenanceId()), input.taskId(), input.operationId(),
                exception.getErrorCode(), reason, input.operatorId());
        return send(command).thenApply(ignored -> new MaintenancePremiumSettlementGateResult(
                MaintenanceWorkflowTaskStatus.FAILED, null, null, null, null, null,
                null, MaintenanceFundSettlementStatus.FAILED, null, null, null,
                exception.getErrorCode(), reason, LocalDateTime.now()));
    }

    private MaintenancePremiumSettlementGateResult result(
            MaintenanceBillingPostingEvidence posting,
            MaintenanceFundSettlementEvidence funds) {
        MaintenanceWorkflowTaskStatus taskStatus = posting.status() == MaintenanceBillingPostingStatus.REVERSED
                || funds.status().failed()
                        ? MaintenanceWorkflowTaskStatus.FAILED
                        : funds.status().completed()
                                ? MaintenanceWorkflowTaskStatus.COMPLETED
                                : MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL;
        return new MaintenancePremiumSettlementGateResult(
                taskStatus, posting.postingId(), posting.status(), posting.direction(),
                posting.amount(), posting.currency(), funds.type(), funds.status(),
                funds.instructionId(), funds.orderId(), funds.externalStatus(),
                funds.failureCode(), funds.failureMessage(), funds.recordedAt());
    }

    private MaintenancePremiumSettlementGateResult result(MaintenanceWorkflowTaskView task) {
        return new MaintenancePremiumSettlementGateResult(
                task.getStatus(), task.getBillingPostingId(), task.getBillingPostingStatus(),
                task.getBillingPostingDirection(), task.getBillingPostingAmount(),
                task.getBillingPostingCurrency(), task.getFundSettlementType(),
                task.getFundSettlementStatus(), task.getFundSettlementInstructionId(),
                task.getFundSettlementOrderId(), task.getFundSettlementExternalStatus(),
                task.getFundSettlementFailureCode(), task.getFundSettlementFailureMessage(),
                task.getFundSettlementRecordedAt());
    }

    private MaintenanceFundSettlementStatus collectionStatus(String externalStatus) {
        return switch (normalizeCode(externalStatus)) {
            case "PENDING" -> MaintenanceFundSettlementStatus.PENDING;
            case "PROCESSING" -> MaintenanceFundSettlementStatus.PROCESSING;
            case "SUCCESS" -> MaintenanceFundSettlementStatus.SUCCEEDED;
            case "FAILED", "CANCELLED" -> MaintenanceFundSettlementStatus.FAILED;
            case "REFUNDED", "PARTIALLY_REFUNDED" -> MaintenanceFundSettlementStatus.REVERSED;
            default -> throw validation("paymentStatus", "无法识别 Payment 收款状态: " + externalStatus);
        };
    }

    private MaintenanceFundSettlementStatus refundStatus(String externalStatus) {
        return switch (normalizeCode(externalStatus)) {
            case "PENDING" -> MaintenanceFundSettlementStatus.PENDING;
            case "PROCESSING", "SUBMITTED" -> MaintenanceFundSettlementStatus.PROCESSING;
            case "SUCCEEDED" -> MaintenanceFundSettlementStatus.SUCCEEDED;
            case "FAILED", "CANCELLED", "ALLOCATION_REQUIRED" ->
                    MaintenanceFundSettlementStatus.FAILED;
            default -> throw validation("refundStatus", "无法识别 Payment 退款状态: " + externalStatus);
        };
    }

    private String paymentOrderId(String tenantId, String postingId, int retryCount) {
        String material = tenantId + ':' + postingId + ":COLLECTION:" + retryCount;
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private CompletableFuture<Void> send(Object command) {
        return commandGateway.send(command).thenApply(ignored -> null);
    }

    private String normalizeCode(String value) {
        if (blank(value)) {
            throw validation("externalStatus", "外部资金状态不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeReason(String value) {
        String reason = blank(value) ? "外部费用结算失败" : value.trim();
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException(
                "MaintenancePremiumSettlementApplicationService", field, message);
    }

    private record SettlementContext(
            MaintenanceView caseView,
            MaintenanceWorkflowTaskView task) {
    }
}
