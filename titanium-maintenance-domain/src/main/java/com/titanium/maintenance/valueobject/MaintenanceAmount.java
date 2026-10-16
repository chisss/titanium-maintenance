package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 保全金额值对象。
 *
 * @param amount 保全金额（不可为空或负数）
 * @deprecated DSN-07 货币建模统一（m25-09，2026-10）：新代码禁止使用，一律改用
 *             {@link com.titanium.metadata.valueobject.Money}（金额 + 币种）。
 *             存量按「先小域后大域」分批迁移：maintenance → regulatory → policy → underwriting → claim。
 */
@Deprecated(since = "2026-10", forRemoval = true)
public record MaintenanceAmount(BigDecimal amount) {

    public MaintenanceAmount {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new MaintenanceValidationException("MaintenanceAmount", "amount", "保全金额不能为空或负数");
        }
    }

    public static MaintenanceAmount of(BigDecimal amount) {
        return new MaintenanceAmount(amount);
    }

    public static MaintenanceAmount zero() {
        return new MaintenanceAmount(BigDecimal.ZERO);
    }
}
