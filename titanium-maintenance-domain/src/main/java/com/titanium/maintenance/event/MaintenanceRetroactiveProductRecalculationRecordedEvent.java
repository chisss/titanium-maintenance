package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodRecalculation;

/** Product 追溯期间重算检查点已记录。 */
public record MaintenanceRetroactiveProductRecalculationRecordedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactivePeriodRecalculation recalculation,
        String recordedBy,
        String tenantId) {
}
