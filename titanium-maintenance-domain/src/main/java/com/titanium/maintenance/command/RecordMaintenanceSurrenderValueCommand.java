package com.titanium.maintenance.command;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录 Product 已确认的退保价值策略证据。 */
public record RecordMaintenanceSurrenderValueCommand(
        @TargetAggregateIdentifier MaintenanceId id,
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
        String updatedBy) {
}
