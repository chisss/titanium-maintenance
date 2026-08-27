package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;

/** 四个权威域取证完成并形成结构化影响清单。 */
public record MaintenanceRetroactiveImpactAnalysisCompletedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactiveImpactAnalysis analysis,
        String completedBy,
        String tenantId) {
}
