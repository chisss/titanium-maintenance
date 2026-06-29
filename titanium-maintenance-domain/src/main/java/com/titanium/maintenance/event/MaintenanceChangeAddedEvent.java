package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.enums.MaintenanceChangeType;
import com.titanium.maintenance.valueobject.MaintenanceId;

public record MaintenanceChangeAddedEvent(MaintenanceId maintenanceId, MaintenanceChangeType changeType,
                                          String fieldName, String oldValue, String newValue, LocalDateTime createdAt,
                                          String createdBy, String tenantId) {
}
