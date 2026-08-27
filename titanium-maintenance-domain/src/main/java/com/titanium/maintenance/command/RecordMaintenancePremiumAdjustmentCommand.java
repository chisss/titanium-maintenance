package com.titanium.maintenance.command;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录 Product 已确认的保全费用差额检查点。 */
public record RecordMaintenancePremiumAdjustmentCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String originalCalculationId,
        String replacementCalculationId,
        String adjustmentId,
        String adjustmentResultHash,
        MaintenanceBalanceDirection direction,
        BigDecimal amount,
        String currency,
        String updatedBy) {
}
