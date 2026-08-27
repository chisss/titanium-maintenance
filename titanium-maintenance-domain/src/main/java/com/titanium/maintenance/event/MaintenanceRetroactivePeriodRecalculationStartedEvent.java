package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodRecalculation;

/** 追溯期间重算请求已冻结。 */
public record MaintenanceRetroactivePeriodRecalculationStartedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactivePeriodRecalculation recalculation,
        String startedBy,
        String tenantId) {
}
