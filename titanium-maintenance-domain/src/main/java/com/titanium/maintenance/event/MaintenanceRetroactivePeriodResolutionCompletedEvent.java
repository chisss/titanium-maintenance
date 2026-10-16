package com.titanium.maintenance.event;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;

/** Billing 关闭会计期间处理结论已记录。 */
@Revision("1")
public record MaintenanceRetroactivePeriodResolutionCompletedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactivePeriodResolution resolution,
        String completedBy,
        String tenantId) {
}
