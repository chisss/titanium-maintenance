package com.titanium.maintenance.application.orchestration.workflow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.MaintenanceItemWithdrawalInput;
import com.titanium.maintenance.application.model.MaintenanceItemWithdrawalResult;
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
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort.ReversalFact;
import com.titanium.maintenance.port.BillingPremiumLifecyclePort.ReversalRequest;
import com.titanium.maintenance.port.PaymentMaintenanceRefundPort;
import com.titanium.maintenance.port.PaymentMaintenanceRefundPort.RefundFact;
import com.titanium.maintenance.port.PaymentMaintenanceRefundPort.RefundRequest;
import com.titanium.maintenance.port.PaymentPremiumCollectionPort;
import com.titanium.maintenance.port.PaymentPremiumCollectionPort.CollectionFact;
import com.titanium.maintenance.port.PaymentPremiumCollectionPort.CollectionRequest;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceBillingReversalEvidence;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawal;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawalCompensation;

import lombok.RequiredArgsConstructor;

/** 单项目撤销与 Billing、Payment 逆向财务处理编排。 */
@Service
@RequiredArgsConstructor
public class MaintenanceItemWithdrawalApplicationService {

    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceCaseItemViewRepository itemViewRepository;
    private final MaintenanceWorkflowTaskViewRepository taskViewRepository;
    private final BillingPremiumLifecyclePort billingPort;
    private final PaymentPremiumCollectionPort collectionPort;
    private final PaymentMaintenanceRefundPort refundPort;

    public CompletableFuture<MaintenanceItemWithdrawalResult> withdraw(
            MaintenanceItemWithdrawalInput input) {
        WithdrawalContext context = requireContext(input);
        String requestHash = requestHash(input);
        StartMaintenanceItemWithdrawalCommand start = new StartMaintenanceItemWithdrawalCommand(
                MaintenanceId.of(input.maintenanceId()), input.itemCode(), input.operationId(), requestHash,
                input.reason(), input.operatorId(), input.tenantId());
        return commandGateway.<MaintenanceItemWithdrawal>send(start)
                .thenCompose(withdrawal -> withdrawal.status() == MaintenanceItemWithdrawalStatus.COMPLETED
                        ? CompletableFuture.completedFuture(result(withdrawal))
                        : configureRecovery(input).thenCompose(ignored -> compensate(input, context, withdrawal)));
    }

    private CompletableFuture<Void> configureRecovery(MaintenanceItemWithdrawalInput input) {
        return commandGateway.<Void>send(new ConfigureMaintenanceItemWithdrawalRecoveryCommand(
                MaintenanceId.of(input.maintenanceId()), input.itemCode(), input.operationId(),
                requestHash(input), normalize(input.paymentMethod()), input.operatorId(), input.tenantId()));
    }

    private CompletableFuture<MaintenanceItemWithdrawalResult> compensate(
            MaintenanceItemWithdrawalInput input,
            WithdrawalContext context,
            MaintenanceItemWithdrawal withdrawal) {
        try {
            MaintenanceWorkflowTaskView feeTask = context.feeTask();
            if (feeTask == null || feeTask.getBillingPostingId() == null) {
                return record(input, withdrawal, notRequired(null, null));
            }
            validateSourcePosting(feeTask);
            SourceFunds sourceFunds = refreshSourceFunds(input, context, feeTask);
            if (sourceFunds.pending()) {
                return record(input, withdrawal, waitingForSourceFunds(feeTask, sourceFunds));
            }
            MaintenanceBillingReversalEvidence reversal = reverse(input, context, feeTask);
            try {
                MaintenanceItemWithdrawalCompensation compensation = sourceFunds.succeeded()
                        ? compensateSucceededFunds(
                                input, context, feeTask, sourceFunds, reversal, withdrawal.retryCount())
                        : notRequired(reversal, sourceFunds.status());
                return record(input, withdrawal, compensation);
            } catch (BusinessException exception) {
                return record(input, withdrawal, failedFunds(feeTask, reversal,
                        exception.getErrorCode(), exception.getMessage()));
            } catch (RuntimeException exception) {
                return record(input, withdrawal, failedFunds(feeTask, reversal,
                        "MAINTENANCE_PAYMENT_COMPENSATION_ERROR", exception.getMessage()));
            }
        } catch (BusinessException exception) {
            return fail(input, withdrawal, exception.getErrorCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            return fail(input, withdrawal, "MAINTENANCE_ITEM_WITHDRAWAL_ERROR", exception.getMessage());
        }
    }

    private SourceFunds refreshSourceFunds(
            MaintenanceItemWithdrawalInput input,
            WithdrawalContext context,
            MaintenanceWorkflowTaskView feeTask) {
        if (feeTask.getFundSettlementType() == null || feeTask.getFundSettlementStatus() == null) {
            throw validation("sourceFunds", "已入账费用任务缺少原资金事实");
        }
        SourceFunds stored = new SourceFunds(
                feeTask.getFundSettlementType(), feeTask.getFundSettlementStatus(),
                feeTask.getFundSettlementOrderId(), feeTask.getFundSettlementExternalStatus());
        if (!stored.pending()) {
            return stored;
        }
        if (stored.orderId() == null) {
            throw validation("sourceFunds", "原资金处理中但缺少资金单号");
        }
        return switch (stored.type()) {
            case COLLECTION -> refreshCollection(input, context, feeTask, stored.orderId());
            case REFUND -> refreshRefund(input, feeTask, stored.orderId());
            case NOT_REQUIRED -> stored;
        };
    }

    private SourceFunds refreshCollection(
            MaintenanceItemWithdrawalInput input,
            WithdrawalContext context,
            MaintenanceWorkflowTaskView feeTask,
            String orderId) {
        CollectionFact fact = collectionPort.get(input.tenantId(), orderId);
        if (!Objects.equals(context.caseView().getPolicyId(), fact.policyId())
                || !Objects.equals(context.caseView().getCustomerId(), fact.customerId())
                || feeTask.getBillingPostingAmount().compareTo(fact.amount()) != 0
                || !feeTask.getBillingPostingCurrency().equalsIgnoreCase(fact.currency())) {
            throw validation("sourceCollection", "原收款单与 Billing 入账事实不一致");
        }
        return new SourceFunds(MaintenanceFundSettlementType.COLLECTION,
                collectionStatus(fact.status()), fact.paymentOrderId(), fact.status());
    }

    private SourceFunds refreshRefund(
            MaintenanceItemWithdrawalInput input,
            MaintenanceWorkflowTaskView feeTask,
            String orderId) {
        RefundFact fact = refundPort.get(input.tenantId(), orderId);
        if (!Objects.equals(feeTask.getBillingPostingId(), fact.sourcePostingId())
                || feeTask.getBillingPostingAmount().compareTo(fact.amount()) != 0
                || !feeTask.getBillingPostingCurrency().equalsIgnoreCase(fact.currency())) {
            throw validation("sourceRefund", "原退款单与 Billing 入账事实不一致");
        }
        return new SourceFunds(MaintenanceFundSettlementType.REFUND,
                refundStatus(fact.status()), fact.refundOrderId(), fact.status());
    }

    private MaintenanceBillingReversalEvidence reverse(
            MaintenanceItemWithdrawalInput input,
            WithdrawalContext context,
            MaintenanceWorkflowTaskView feeTask) {
        String requestId = deterministicId(input.tenantId(), input.maintenanceId(), input.itemCode(),
                input.operationId(), "BILLING_REVERSAL");
        ReversalFact fact = billingPort.reverse(new ReversalRequest(
                input.tenantId(), feeTask.getBillingPostingId(), requestId,
                input.reason(), input.operatorId()));
        if (fact == null || !requestId.equals(fact.requestId())
                || !feeTask.getBillingPostingId().equals(fact.sourcePostingId())
                || !feeTask.getBillingResultHash().equalsIgnoreCase(fact.sourceResultHash())
                || !context.caseView().getPolicyId().equals(fact.policyId())
                || !context.caseView().getCustomerId().equals(fact.customerId())) {
            throw validation("billingReversal", "Billing 冲正结果未通过案件与原入账勾稽");
        }
        return new MaintenanceBillingReversalEvidence(
                fact.reversalId(), fact.requestId(), fact.requestHash(), fact.resultHash(),
                fact.sourcePostingId(), fact.sourceResultHash(), fact.direction(), fact.amount(),
                fact.currency(), fact.status(), fact.createdAt());
    }

    private MaintenanceItemWithdrawalCompensation compensateSucceededFunds(
            MaintenanceItemWithdrawalInput input,
            WithdrawalContext context,
            MaintenanceWorkflowTaskView feeTask,
            SourceFunds sourceFunds,
            MaintenanceBillingReversalEvidence reversal,
            int retryCount) {
        return switch (feeTask.getBillingPostingDirection()) {
            case DEBIT -> refundOriginalCollection(input, feeTask, sourceFunds, reversal);
            case CREDIT -> collectOriginalRefund(input, context, feeTask, reversal, retryCount);
            case NONE -> throw validation("billingDirection", "NONE 入账不允许冲正");
        };
    }

    private MaintenanceItemWithdrawalCompensation refundOriginalCollection(
            MaintenanceItemWithdrawalInput input,
            MaintenanceWorkflowTaskView feeTask,
            SourceFunds sourceFunds,
            MaintenanceBillingReversalEvidence reversal) {
        String requestId = deterministicId(input.tenantId(), input.maintenanceId(), input.itemCode(),
                input.operationId(), "PAYMENT_REFUND");
        RefundFact fact = refundPort.create(new RefundRequest(
                input.tenantId(), requestId, reversal.reversalId(), sourceFunds.orderId(),
                feeTask.getBillingPostingAmount(), feeTask.getBillingPostingCurrency(),
                input.reason(), input.operatorId()));
        MaintenanceFundSettlementStatus status = refundStatus(fact.status());
        return compensation(reversal, MaintenanceItemWithdrawalFundAction.REFUND, status,
                fact.refundRequestId(), fact.refundOrderId(), fact.status(),
                feeTask, fact.failureCode(), fact.failureMessage());
    }

    private MaintenanceItemWithdrawalCompensation collectOriginalRefund(
            MaintenanceItemWithdrawalInput input,
            WithdrawalContext context,
            MaintenanceWorkflowTaskView feeTask,
            MaintenanceBillingReversalEvidence reversal,
            int retryCount) {
        if (blank(input.paymentMethod())) {
            throw validation("paymentMethod", "追回已成功退款时必须指定支付渠道");
        }
        String orderId = deterministicId(input.tenantId(), reversal.reversalId(),
                "PAYMENT_COLLECTION", Integer.toString(retryCount));
        CollectionFact fact = collectionPort.create(new CollectionRequest(
                input.tenantId(), orderId, context.caseView().getPolicyId(),
                context.caseView().getCustomerId(), feeTask.getBillingPostingAmount(),
                feeTask.getBillingPostingCurrency(), input.paymentMethod(), input.reason()));
        MaintenanceFundSettlementStatus status = collectionStatus(fact.status());
        return compensation(reversal, MaintenanceItemWithdrawalFundAction.COLLECTION, status,
                orderId, fact.paymentOrderId(), fact.status(), feeTask,
                status.failed() ? "PAYMENT_COLLECTION_" + normalizeCode(fact.status()) : null,
                status.failed() ? "Payment 补收状态为 " + fact.status() : null);
    }

    private MaintenanceItemWithdrawalCompensation waitingForSourceFunds(
            MaintenanceWorkflowTaskView feeTask,
            SourceFunds sourceFunds) {
        MaintenanceItemWithdrawalFundAction action = feeTask.getBillingPostingDirection()
                == MaintenanceBalanceDirection.DEBIT
                        ? MaintenanceItemWithdrawalFundAction.REFUND
                        : MaintenanceItemWithdrawalFundAction.COLLECTION;
        return new MaintenanceItemWithdrawalCompensation(
                null, sourceFunds.status(), action, sourceFunds.status(), null, sourceFunds.orderId(),
                sourceFunds.externalStatus(), feeTask.getBillingPostingAmount(),
                feeTask.getBillingPostingCurrency(), null, null, LocalDateTime.now());
    }

    private MaintenanceItemWithdrawalCompensation notRequired(
            MaintenanceBillingReversalEvidence reversal,
            MaintenanceFundSettlementStatus sourceFundStatus) {
        return new MaintenanceItemWithdrawalCompensation(
                reversal, sourceFundStatus, MaintenanceItemWithdrawalFundAction.NOT_REQUIRED,
                MaintenanceFundSettlementStatus.NOT_REQUIRED, null, null, "NOT_REQUIRED",
                reversal == null ? BigDecimal.ZERO : reversal.amount(),
                reversal == null ? null : reversal.currency(), null, null, LocalDateTime.now());
    }

    private MaintenanceItemWithdrawalCompensation failedFunds(
            MaintenanceWorkflowTaskView feeTask,
            MaintenanceBillingReversalEvidence reversal,
            String failureCode,
            String failureMessage) {
        MaintenanceItemWithdrawalFundAction action = feeTask.getBillingPostingDirection()
                == MaintenanceBalanceDirection.DEBIT
                        ? MaintenanceItemWithdrawalFundAction.REFUND
                        : MaintenanceItemWithdrawalFundAction.COLLECTION;
        return new MaintenanceItemWithdrawalCompensation(
                reversal, MaintenanceFundSettlementStatus.SUCCEEDED, action,
                MaintenanceFundSettlementStatus.FAILED,
                null, null, "REMOTE_ERROR", feeTask.getBillingPostingAmount(),
                feeTask.getBillingPostingCurrency(),
                defaultText(failureCode, "MAINTENANCE_PAYMENT_COMPENSATION_ERROR"),
                safeMessage(failureMessage), LocalDateTime.now());
    }

    private MaintenanceItemWithdrawalCompensation compensation(
            MaintenanceBillingReversalEvidence reversal,
            MaintenanceItemWithdrawalFundAction action,
            MaintenanceFundSettlementStatus status,
            String requestId,
            String orderId,
            String externalStatus,
            MaintenanceWorkflowTaskView feeTask,
            String externalFailureCode,
            String externalFailureMessage) {
        String failureCode = status.failed()
                ? defaultText(externalFailureCode, "PAYMENT_" + action.getCode() + "_FAILED") : null;
        String failureMessage = status.failed()
                ? defaultText(externalFailureMessage, "Payment 逆向资金处理失败") : null;
        return new MaintenanceItemWithdrawalCompensation(
                reversal, MaintenanceFundSettlementStatus.SUCCEEDED, action, status,
                requestId, orderId, externalStatus,
                feeTask.getBillingPostingAmount(), feeTask.getBillingPostingCurrency(),
                failureCode, failureMessage, LocalDateTime.now());
    }

    private CompletableFuture<MaintenanceItemWithdrawalResult> record(
            MaintenanceItemWithdrawalInput input,
            MaintenanceItemWithdrawal withdrawal,
            MaintenanceItemWithdrawalCompensation compensation) {
        RecordMaintenanceItemWithdrawalCompensationCommand command =
                new RecordMaintenanceItemWithdrawalCompensationCommand(
                        MaintenanceId.of(input.maintenanceId()), input.itemCode(),
                        withdrawal.operationId(), withdrawal.requestHash(), compensation, input.operatorId());
        return commandGateway.<MaintenanceItemWithdrawal>send(command).thenApply(this::result);
    }

    private CompletableFuture<MaintenanceItemWithdrawalResult> fail(
            MaintenanceItemWithdrawalInput input,
            MaintenanceItemWithdrawal withdrawal,
            String failureCode,
            String failureMessage) {
        FailMaintenanceItemWithdrawalCommand command = new FailMaintenanceItemWithdrawalCommand(
                MaintenanceId.of(input.maintenanceId()), input.itemCode(), withdrawal.operationId(),
                withdrawal.requestHash(), defaultText(failureCode, "MAINTENANCE_ITEM_WITHDRAWAL_ERROR"),
                safeMessage(failureMessage), input.operatorId());
        return commandGateway.<MaintenanceItemWithdrawal>send(command).thenApply(this::result);
    }

    private WithdrawalContext requireContext(MaintenanceItemWithdrawalInput input) {
        if (input == null || blank(input.maintenanceId()) || blank(input.itemCode())
                || blank(input.operationId()) || blank(input.reason()) || blank(input.operatorId())
                || blank(input.tenantId())) {
            throw validation("input", "项目撤销操作上下文不完整");
        }
        MaintenanceView caseView = maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        input.maintenanceId(), input.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
        MaintenanceCaseItemView itemView = itemViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCode(
                        input.tenantId(), input.maintenanceId(), input.itemCode())
                .orElseThrow(MaintenanceNotFoundException::new);
        List<MaintenanceWorkflowTaskView> feeTasks = taskViewRepository
                .findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                        input.tenantId(), input.maintenanceId()).stream()
                .filter(task -> task.getItemCode().equals(input.itemCode()))
                .filter(task -> task.getStepType() == MaintenanceStepType.FEE_SETTLEMENT)
                .filter(task -> task.getBillingPostingId() != null)
                .toList();
        if (feeTasks.size() > 1) {
            throw validation("feeTask", "同一项目存在多条已入账费用任务");
        }
        return new WithdrawalContext(caseView, itemView, feeTasks.isEmpty() ? null : feeTasks.getFirst());
    }

    private void validateSourcePosting(MaintenanceWorkflowTaskView task) {
        if (task.getBillingPostingStatus() != MaintenanceBillingPostingStatus.POSTED
                || task.getBillingResultHash() == null || task.getBillingPostingDirection() == null
                || task.getBillingPostingDirection() == MaintenanceBalanceDirection.NONE
                || task.getBillingPostingAmount() == null || task.getBillingPostingAmount().signum() <= 0
                || task.getBillingPostingCurrency() == null) {
            throw validation("sourcePosting", "原 Billing 入账事实不完整或已冲正");
        }
    }

    private MaintenanceItemWithdrawalResult result(MaintenanceItemWithdrawal withdrawal) {
        var compensation = withdrawal.compensation();
        var reversal = compensation == null ? null : compensation.reversal();
        return new MaintenanceItemWithdrawalResult(
                withdrawal.itemCode(), withdrawal.operationId(), withdrawal.requestHash(), withdrawal.status(),
                withdrawal.sourcePosting() == null ? null : withdrawal.sourcePosting().postingId(),
                compensation == null ? null : compensation.sourceFundStatus(),
                reversal == null ? null : reversal.reversalId(),
                reversal == null ? null : reversal.resultHash(),
                reversal == null ? null : reversal.direction(),
                compensation == null ? null : compensation.amount(),
                compensation == null ? null : compensation.currency(),
                compensation == null ? null : compensation.fundAction(),
                compensation == null ? null : compensation.fundStatus(),
                compensation == null ? null : compensation.fundRequestId(),
                compensation == null ? null : compensation.fundOrderId(),
                compensation == null ? null : compensation.fundExternalStatus(),
                withdrawal.failureCode(), withdrawal.failureMessage(), withdrawal.retryCount(),
                withdrawal.requestedAt(), withdrawal.completedAt());
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
            case "FAILED", "CANCELLED", "ALLOCATION_REQUIRED" -> MaintenanceFundSettlementStatus.FAILED;
            default -> throw validation("refundStatus", "无法识别 Payment 退款状态: " + externalStatus);
        };
    }

    private String requestHash(MaintenanceItemWithdrawalInput input) {
        return sha256(input.tenantId(), input.maintenanceId(), input.itemCode(),
                input.reason().trim(), normalize(input.paymentMethod()));
    }

    private String deterministicId(String... values) {
        return UUID.nameUUIDFromBytes(String.join(":", values).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String sha256(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) ':');
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }

    private String normalizeCode(String value) {
        if (blank(value)) {
            throw validation("externalStatus", "外部资金状态不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return blank(value) ? "" : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private String safeMessage(String value) {
        String message = defaultText(value, "项目撤销外部处理失败");
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException(
                "MaintenanceItemWithdrawalApplicationService", field, message);
    }

    private record WithdrawalContext(
            MaintenanceView caseView,
            MaintenanceCaseItemView itemView,
            MaintenanceWorkflowTaskView feeTask) {
    }

    private record SourceFunds(
            MaintenanceFundSettlementType type,
            MaintenanceFundSettlementStatus status,
            String orderId,
            String externalStatus) {

        private boolean pending() {
            return status == MaintenanceFundSettlementStatus.PENDING
                    || status == MaintenanceFundSettlementStatus.PROCESSING;
        }

        private boolean succeeded() {
            return status == MaintenanceFundSettlementStatus.SUCCEEDED;
        }
    }
}
