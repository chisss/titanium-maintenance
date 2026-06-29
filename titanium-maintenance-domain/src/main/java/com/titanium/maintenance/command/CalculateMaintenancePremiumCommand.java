package com.titanium.maintenance.command;

import java.math.BigDecimal;

import com.titanium.maintenance.valueobject.MaintenanceId;

import lombok.Builder;
import lombok.Getter;

/**
 * 计算保全保费命令（领域层）
 */
@Getter
@Builder
public class CalculateMaintenancePremiumCommand {
    private final MaintenanceId id;
    private final BigDecimal    totalAmount;
    private final BigDecimal    refundAmount;
    private final String        calculationDetails;
    private final String        updatedBy;
}
