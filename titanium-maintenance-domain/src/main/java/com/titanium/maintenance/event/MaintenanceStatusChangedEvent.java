package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.enums.MaintenanceStatus;
import com.titanium.maintenance.valueobject.MaintenanceId;

public record MaintenanceStatusChangedEvent(MaintenanceId maintenanceId, MaintenanceStatus oldStatus,
                                            MaintenanceStatus newStatus, String changeReason, LocalDateTime changedAt,
                                            String changedBy, String tenantId) {
}
