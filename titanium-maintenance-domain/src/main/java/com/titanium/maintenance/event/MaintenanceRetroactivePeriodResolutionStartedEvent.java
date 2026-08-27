package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;

/** 关闭会计期间处理请求已冻结。 */
public record MaintenanceRetroactivePeriodResolutionStartedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactivePeriodResolution resolution,
        String startedBy,
        String tenantId) {
}
