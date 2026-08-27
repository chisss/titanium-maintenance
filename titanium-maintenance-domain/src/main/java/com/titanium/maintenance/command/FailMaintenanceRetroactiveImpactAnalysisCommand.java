package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 记录追溯影响取证失败，保留同操作重试所需事实。 */
public record FailMaintenanceRetroactiveImpactAnalysisCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String analysisId,
        String operationId,
        String failureCode,
        String failureMessage,
        LocalDateTime failedAt,
        String operatorId) {
}
