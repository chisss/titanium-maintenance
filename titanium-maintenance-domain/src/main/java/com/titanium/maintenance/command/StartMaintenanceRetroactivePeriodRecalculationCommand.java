package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 冻结追溯期间重算请求及影响分析版本。 */
public record StartMaintenanceRetroactivePeriodRecalculationCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String periodRecalculationId,
        String operationId,
        String requestHash,
        String analysisId,
        int analysisVersion,
        String analysisResultHash,
        LocalDateTime startedAt,
        String operatorId) {
}
