package com.titanium.maintenance.valueobject.withdrawal;

import java.time.LocalDateTime;
import java.util.Objects;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalFundAction;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.workflow.MaintenanceBillingPostingEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceFundSettlementEvidence;

/** 案件内单个保全项目的撤销状态与完整财务补偿证据。 */
public record MaintenanceItemWithdrawal(
        String itemCode,
        String operationId,
        String requestHash,
        String reason,
        MaintenanceItemWithdrawalStatus status,
        MaintenanceBillingPostingEvidence sourcePosting,
        MaintenanceFundSettlementEvidence sourceFunds,
        MaintenanceItemWithdrawalCompensation compensation,
        String failureCode,
        String failureMessage,
        int retryCount,
        LocalDateTime requestedAt,
        String requestedBy,
        LocalDateTime updatedAt,
        LocalDateTime completedAt) {

    public MaintenanceItemWithdrawal {
        if (!hasText(itemCode) || !hasText(operationId) || !hash(requestHash) || !hasText(reason)
                || status == null || retryCount < 0 || requestedAt == null || !hasText(requestedBy)
                || updatedAt == null) {
            throw validation("withdrawal", "项目撤销事实字段不完整");
        }
        failureCode = normalize(failureCode);
        failureMessage = normalize(failureMessage);
        if (sourceFunds != null && sourcePosting == null) {
            throw validation("sourceFunds", "原资金事实必须关联原 Billing 入账");
        }
        if (status == MaintenanceItemWithdrawalStatus.COMPLETED && completedAt == null) {
            throw validation("completedAt", "已完成撤销必须记录完成时间");
        }
        if (status == MaintenanceItemWithdrawalStatus.FAILED
                && (!hasText(failureCode) || !hasText(failureMessage))) {
            throw validation("failure", "撤销失败必须记录失败码和失败原因");
        }
    }

    public static MaintenanceItemWithdrawal requested(
            String itemCode,
            String operationId,
            String requestHash,
            String reason,
            MaintenanceBillingPostingEvidence sourcePosting,
            MaintenanceFundSettlementEvidence sourceFunds,
            LocalDateTime requestedAt,
            String requestedBy) {
        return new MaintenanceItemWithdrawal(
                itemCode, operationId, requestHash, reason.trim(), MaintenanceItemWithdrawalStatus.REQUESTED,
                sourcePosting, sourceFunds, null, null, null, 0,
                requestedAt, requestedBy, requestedAt, null);
    }

    /** 同一操作可从等待或失败状态继续，完成后不允许替换权威补偿事实。 */
    public MaintenanceItemWithdrawal recordCompensation(
            MaintenanceItemWithdrawalCompensation evidence) {
        if (evidence == null) {
            throw validation("compensation", "项目撤销补偿事实不能为空");
        }
        if (status == MaintenanceItemWithdrawalStatus.COMPLETED) {
            if (Objects.equals(compensation, evidence)) {
                return this;
            }
            throw validation("compensation", "已完成撤销不能替换补偿事实");
        }
        validateCompensation(evidence);
        MaintenanceItemWithdrawalStatus next = evidence.completed()
                ? MaintenanceItemWithdrawalStatus.COMPLETED
                : evidence.fundStatus().failed()
                        ? MaintenanceItemWithdrawalStatus.FAILED
                        : MaintenanceItemWithdrawalStatus.WAITING_FUNDS;
        String nextFailureCode = next == MaintenanceItemWithdrawalStatus.FAILED
                ? evidence.failureCode() : null;
        String nextFailureMessage = next == MaintenanceItemWithdrawalStatus.FAILED
                ? evidence.failureMessage() : null;
        int nextRetryCount = next == MaintenanceItemWithdrawalStatus.FAILED
                ? retryCount + 1 : retryCount;
        return new MaintenanceItemWithdrawal(
                itemCode, operationId, requestHash, reason, next, sourcePosting, sourceFunds,
                evidence, nextFailureCode, nextFailureMessage, nextRetryCount,
                requestedAt, requestedBy, evidence.recordedAt(),
                next == MaintenanceItemWithdrawalStatus.COMPLETED ? evidence.recordedAt() : null);
    }

    public MaintenanceItemWithdrawal fail(
            String code,
            String message,
            LocalDateTime failedAt) {
        if (status == MaintenanceItemWithdrawalStatus.COMPLETED) {
            throw validation("status", "已完成撤销不能记录失败");
        }
        return new MaintenanceItemWithdrawal(
                itemCode, operationId, requestHash, reason, MaintenanceItemWithdrawalStatus.FAILED,
                sourcePosting, sourceFunds, compensation, requireText("failureCode", code),
                requireText("failureMessage", message), retryCount + 1,
                requestedAt, requestedBy, failedAt, null);
    }

    public boolean sameRequest(String candidateOperationId, String candidateRequestHash) {
        return operationId.equals(candidateOperationId) && requestHash.equalsIgnoreCase(candidateRequestHash);
    }

    private void validateCompensation(MaintenanceItemWithdrawalCompensation evidence) {
        if (sourcePosting == null) {
            if (evidence.reversal() != null || evidence.sourceFundStatus() != null
                    || evidence.fundAction() != MaintenanceItemWithdrawalFundAction.NOT_REQUIRED
                    || evidence.amount().signum() != 0) {
                throw validation("compensation", "无原费用事实的项目不能产生财务补偿");
            }
            return;
        }
        validateSourceFundStatus(evidence.sourceFundStatus());
        MaintenanceItemWithdrawalFundAction expectedAction = sourcePosting.direction()
                == MaintenanceBalanceDirection.DEBIT
                        ? MaintenanceItemWithdrawalFundAction.REFUND
                        : MaintenanceItemWithdrawalFundAction.COLLECTION;
        if (evidence.reversal() == null) {
            if (evidence.fundStatus() != MaintenanceFundSettlementStatus.PENDING
                    && evidence.fundStatus() != MaintenanceFundSettlementStatus.PROCESSING) {
                throw validation("reversal", "未完成 Billing 冲正时只能等待原资金终态");
            }
            if (evidence.sourceFundStatus() != evidence.fundStatus()
                    || evidence.fundAction() != expectedAction) {
                throw validation("sourceFunds", "等待状态必须与原资金当前状态和逆向动作一致");
            }
            return;
        }
        validateReversal(evidence.reversal());
        if (sourcePosting.amount().compareTo(evidence.amount()) != 0
                || !sourcePosting.currency().equalsIgnoreCase(evidence.currency())) {
            throw validation("amount", "补偿资金金额或币种与原入账不一致");
        }
        if (evidence.sourceFundStatus() == MaintenanceFundSettlementStatus.SUCCEEDED
                && evidence.fundAction() != expectedAction) {
            throw validation("fundAction", "原资金成功后必须执行方向相反的退款或补收");
        }
        if (evidence.sourceFundStatus() != MaintenanceFundSettlementStatus.SUCCEEDED
                && evidence.fundAction() != MaintenanceItemWithdrawalFundAction.NOT_REQUIRED) {
            throw validation("fundAction", "原资金未成功时只需冲正 Billing 入账");
        }
    }

    private void validateSourceFundStatus(MaintenanceFundSettlementStatus current) {
        if (current == null || sourceFunds == null) {
            throw validation("sourceFundStatus", "原 Billing 入账必须携带原资金状态");
        }
        MaintenanceFundSettlementStatus frozen = sourceFunds.status();
        boolean mutablePending = frozen == MaintenanceFundSettlementStatus.PENDING
                || frozen == MaintenanceFundSettlementStatus.PROCESSING;
        if (!mutablePending && current != frozen) {
            throw validation("sourceFundStatus", "原资金终态不能被撤销流程改写");
        }
    }

    private void validateReversal(MaintenanceBillingReversalEvidence reversal) {
        MaintenanceBalanceDirection expectedDirection = switch (sourcePosting.direction()) {
            case DEBIT -> MaintenanceBalanceDirection.CREDIT;
            case CREDIT -> MaintenanceBalanceDirection.DEBIT;
            case NONE -> throw validation("sourcePosting", "NONE 入账不允许冲正");
        };
        if (!sourcePosting.postingId().equals(reversal.sourcePostingId())
                || !sourcePosting.resultHash().equalsIgnoreCase(reversal.sourceResultHash())
                || expectedDirection != reversal.direction()
                || sourcePosting.amount().compareTo(reversal.amount()) != 0
                || !sourcePosting.currency().equalsIgnoreCase(reversal.currency())) {
            throw validation("reversal", "Billing 冲正结果与原入账事实不一致");
        }
    }

    private static String requireText(String field, String value) {
        if (!hasText(value)) {
            throw validation(field, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private static MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException("MaintenanceItemWithdrawal", field, message);
    }
}
