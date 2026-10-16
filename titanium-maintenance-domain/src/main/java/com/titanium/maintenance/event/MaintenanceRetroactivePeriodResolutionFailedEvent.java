package com.titanium.maintenance.event;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;

/** 关闭会计期间处理失败。 */
@Revision("1")
public record MaintenanceRetroactivePeriodResolutionFailedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactivePeriodResolution resolution,
        String failedBy,
        String tenantId) {
}
