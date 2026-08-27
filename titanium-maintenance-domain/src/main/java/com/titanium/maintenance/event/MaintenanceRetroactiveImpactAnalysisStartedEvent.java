package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;

/** 追溯影响分析范围和幂等请求已冻结。 */
public record MaintenanceRetroactiveImpactAnalysisStartedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactiveImpactAnalysis analysis,
        String startedBy,
        String tenantId) {
}
