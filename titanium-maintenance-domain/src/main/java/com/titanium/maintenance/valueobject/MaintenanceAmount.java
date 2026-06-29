package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;

import com.titanium.maintenance.exception.MaintenanceValidationException;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class MaintenanceAmount {
    private final BigDecimal amount;

    private MaintenanceAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new MaintenanceValidationException("MaintenanceAmount", "amount", "保全金额不能为空或负数");
        }
        this.amount = amount;
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
