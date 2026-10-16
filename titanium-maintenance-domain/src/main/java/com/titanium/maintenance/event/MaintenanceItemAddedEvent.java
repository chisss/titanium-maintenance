package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;

/** 保全项配置版本已冻结到案件。 */
@Revision("1")
public record MaintenanceItemAddedEvent(MaintenanceId maintenanceId, MaintenanceItemInstance item,
        LocalDateTime addedAt, String createdBy, String tenantId) {
}
