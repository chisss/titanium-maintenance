package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 冻结一次追溯影响分析的版本、范围和幂等请求。 */
public record StartMaintenanceRetroactiveImpactAnalysisCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String analysisId,
        String operationId,
        String requestHash,
        LocalDateTime scopeFrom,
        LocalDateTime scopeTo,
        LocalDateTime startedAt,
        String operatorId) {
}
