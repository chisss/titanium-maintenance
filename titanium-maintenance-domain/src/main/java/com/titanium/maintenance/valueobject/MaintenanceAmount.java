package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 保全金额值对象。
 *
 * @param amount 保全金额（不可为空或负数）
 */
public record MaintenanceAmount(BigDecimal amount) {

    public MaintenanceAmount {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new MaintenanceValidationException("MaintenanceAmount", "amount", "保全金额不能为空或负数");
        }
    }

    public static MaintenanceAmount of(BigDecimal amount) {
        return new MaintenanceAmount(amount);
    }

    public static MaintenanceAmount of(double amount) {
        return new MaintenanceAmount(BigDecimal.valueOf(amount));
    }

    public static MaintenanceAmount zero() {
        return new MaintenanceAmount(BigDecimal.ZERO);
    }
}
