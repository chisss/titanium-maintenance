package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductRecalculationEvidence;

/** 记录 Product 追溯期间重算权威检查点。 */
public record RecordMaintenanceRetroactiveProductRecalculationCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String periodRecalculationId,
        String operationId,
        MaintenanceRetroactiveProductRecalculationEvidence evidence,
        LocalDateTime recordedAt,
        String operatorId) {
}
