package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodRecalculation;

/** Billing 期间调整已记录，期间重算进入完成或人工复核终态。 */
public record MaintenanceRetroactivePeriodRecalculationCompletedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactivePeriodRecalculation recalculation,
        String completedBy,
        String tenantId) {
}
