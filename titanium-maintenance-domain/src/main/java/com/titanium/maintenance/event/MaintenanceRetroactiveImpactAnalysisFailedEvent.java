package com.titanium.maintenance.event;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;

/** 追溯影响分析因权威取证失败而终止。 */
@Revision("1")
public record MaintenanceRetroactiveImpactAnalysisFailedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactiveImpactAnalysis analysis,
        String failedBy,
        String tenantId) {
}
