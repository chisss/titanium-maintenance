package com.titanium.maintenance.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** Product 退保价值策略证据已记录。 */
public record MaintenanceSurrenderValueRecordedEvent(
        MaintenanceId maintenanceId,
        String adjustmentId,
        String policyCode,
        String policyVersion,
        String policyContentHash,
        Integer policyYear,
        Integer coolingOffDays,
        String refundType,
        Boolean withinCoolingOff,
        BigDecimal cashValueRate,
        BigDecimal retainedCustomerAmount,
        BigDecimal internalCostRetentionRate,
        LocalDateTime recordedAt,
        String updatedBy,
        String tenantId) {
}
