package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 保存关闭期间处理失败事实。 */
public record FailMaintenanceRetroactivePeriodResolutionCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String periodResolutionId,
        String operationId,
        String failureCode,
        String failureMessage,
        LocalDateTime failedAt,
        String operatorId) {
}
