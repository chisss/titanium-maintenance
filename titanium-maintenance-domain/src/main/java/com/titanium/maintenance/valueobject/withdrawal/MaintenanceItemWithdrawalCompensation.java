package com.titanium.maintenance.valueobject.withdrawal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceItemWithdrawalFundAction;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 项目撤销对应的 Billing 冲正与 Payment 逆向资金事实。 */
public record MaintenanceItemWithdrawalCompensation(
        MaintenanceBillingReversalEvidence reversal,
        MaintenanceFundSettlementStatus sourceFundStatus,
        MaintenanceItemWithdrawalFundAction fundAction,
        MaintenanceFundSettlementStatus fundStatus,
        String fundRequestId,
        String fundOrderId,
        String fundExternalStatus,
        BigDecimal amount,
        String currency,
        String failureCode,
        String failureMessage,
        LocalDateTime recordedAt) {

    public MaintenanceItemWithdrawalCompensation {
        if (fundAction == null || fundStatus == null || amount == null || amount.signum() < 0
                || recordedAt == null) {
            throw validation("补偿资金事实字段不完整");
        }
        currency = hasText(currency) ? currency.trim().toUpperCase() : null;
        fundRequestId = normalize(fundRequestId);
        fundOrderId = normalize(fundOrderId);
        fundExternalStatus = normalize(fundExternalStatus);
        failureCode = normalize(failureCode);
        failureMessage = normalize(failureMessage);
        if (fundAction == MaintenanceItemWithdrawalFundAction.NOT_REQUIRED
                && (fundStatus != MaintenanceFundSettlementStatus.NOT_REQUIRED
                        || fundRequestId != null || fundOrderId != null)) {
            throw validation("无需资金处理不能携带资金单据");
        }
        if (fundAction != MaintenanceItemWithdrawalFundAction.NOT_REQUIRED
                && (fundStatus == MaintenanceFundSettlementStatus.NOT_REQUIRED
                        || !hasText(fundExternalStatus) || !hasText(currency))) {
            throw validation("退款或补收必须携带外部资金状态");
        }
        if (fundStatus == MaintenanceFundSettlementStatus.SUCCEEDED
                && (!hasText(fundRequestId) || !hasText(fundOrderId))) {
            throw validation("资金成功必须携带请求号和资金单号");
        }
        if (fundStatus.failed() && (!hasText(failureCode) || !hasText(failureMessage))) {
            throw validation("资金失败必须携带失败码和失败原因");
        }
    }

    public boolean completed() {
        return fundStatus == MaintenanceFundSettlementStatus.NOT_REQUIRED
                || fundStatus == MaintenanceFundSettlementStatus.SUCCEEDED;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static MaintenanceValidationException validation(String message) {
        return new MaintenanceValidationException(
                "MaintenanceItemWithdrawalCompensation", "compensation", message);
    }
}
