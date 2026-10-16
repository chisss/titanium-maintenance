package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.common.enums.MaintenanceChangeType;
import com.titanium.maintenance.valueobject.MaintenanceId;

@Revision("1")
public record MaintenanceChangeAddedEvent(MaintenanceId maintenanceId, MaintenanceChangeType changeType,
                                          String fieldName, String oldValue, String newValue, LocalDateTime createdAt,
                                          String createdBy, String tenantId) {
}
