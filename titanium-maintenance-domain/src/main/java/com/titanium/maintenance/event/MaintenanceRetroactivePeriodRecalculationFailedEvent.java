package com.titanium.maintenance.event;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodRecalculation;

/** 追溯期间重算失败，已成功的上游检查点继续保留。 */
@Revision("1")
public record MaintenanceRetroactivePeriodRecalculationFailedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactivePeriodRecalculation recalculation,
        String failedBy,
        String tenantId) {
}
