package com.titanium.maintenance.event;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;

/** 关闭会计期间处理请求已冻结。 */
@Revision("1")
public record MaintenanceRetroactivePeriodResolutionStartedEvent(
        MaintenanceId maintenanceId,
        MaintenanceRetroactivePeriodResolution resolution,
        String startedBy,
        String tenantId) {
}
