package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;

/** 关闭会计期间处理失败。 */
public record MaintenanceRetroactivePeriodResolutionFailedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactivePeriodResolution resolution,
        String failedBy,
        String tenantId) {
}
