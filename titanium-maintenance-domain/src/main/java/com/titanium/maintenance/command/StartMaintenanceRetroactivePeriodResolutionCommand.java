package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 冻结关闭会计期间处理请求。 */
public record StartMaintenanceRetroactivePeriodResolutionCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String periodResolutionId,
        String operationId,
        String requestHash,
        String billingBatchId,
        String sourceBatchResultHash,
        String targetAccountingPeriod,
        String reason,
        LocalDateTime startedAt,
        String operatorId) {
}
