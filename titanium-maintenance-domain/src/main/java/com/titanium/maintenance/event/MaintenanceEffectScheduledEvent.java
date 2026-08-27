package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectSchedule;

/** 案件未来生效计划已建立。 */
public record MaintenanceEffectScheduledEvent(
        MaintenanceId maintenanceId,
        MaintenanceEffectSchedule schedule,
        String scheduledBy,
        String tenantId) {
}
