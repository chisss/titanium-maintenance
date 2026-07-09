package com.titanium.maintenance.command;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/**
 * 计算保全保费命令（领域层）
 */
public record CalculateMaintenancePremiumCommand(@TargetAggregateIdentifier MaintenanceId id, BigDecimal totalAmount,
        BigDecimal refundAmount, String calculationDetails, String updatedBy) {
}
