package com.titanium.maintenance.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.valueobject.MaintenanceId;

/** Product 保全费用差额事实已记录。 */
public record MaintenancePremiumAdjustmentRecordedEvent(
        MaintenanceId maintenanceId,
        String originalCalculationId,
        String replacementCalculationId,
        String adjustmentId,
        String adjustmentResultHash,
        MaintenanceBalanceDirection direction,
        BigDecimal amount,
        String currency,
        LocalDateTime recordedAt,
        String updatedBy,
        String tenantId) {
}
