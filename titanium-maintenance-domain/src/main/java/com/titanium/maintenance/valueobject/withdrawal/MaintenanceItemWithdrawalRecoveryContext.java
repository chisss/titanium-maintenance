package com.titanium.maintenance.valueobject.withdrawal;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 项目撤销自动恢复所需的不可变业务输入。 */
public record MaintenanceItemWithdrawalRecoveryContext(
        String itemCode,
        String operationId,
        String requestHash,
        String paymentMethod,
        LocalDateTime configuredAt,
        String configuredBy) {

    public MaintenanceItemWithdrawalRecoveryContext {
        itemCode = requireText("itemCode", itemCode);
        operationId = requireText("operationId", operationId);
        if (requestHash == null || !requestHash.matches("[0-9a-fA-F]{64}")) {
            throw validation("requestHash", "恢复上下文请求摘要必须为 SHA-256");
        }
        paymentMethod = hasText(paymentMethod) ? paymentMethod.trim() : null;
        if (configuredAt == null) {
            throw validation("configuredAt", "恢复上下文配置时间不能为空");
        }
        configuredBy = requireText("configuredBy", configuredBy);
    }

    private static String requireText(String field, String value) {
        if (!hasText(value)) {
            throw validation(field, "字段不能为空");
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException("MaintenanceItemWithdrawalRecoveryContext", field, message);
    }
}
