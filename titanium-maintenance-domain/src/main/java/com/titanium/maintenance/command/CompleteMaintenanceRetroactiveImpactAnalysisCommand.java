package com.titanium.maintenance.command;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;

/** 记录四个权威域均已覆盖的追溯影响分析结果。 */
public record CompleteMaintenanceRetroactiveImpactAnalysisCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String analysisId,
        String operationId,
        List<MaintenanceRetroactiveImpactDomain> coveredDomains,
        List<MaintenanceRetroactiveImpactItem> items,
        String evidenceVersion,
        String resultHash,
        LocalDateTime completedAt,
        String operatorId) {
}
