package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.valueobject.MaintenanceId;

@Revision("1")
public record MaintenanceStatusChangedEvent(MaintenanceId maintenanceId, MaintenanceStatus oldStatus,
                                            MaintenanceStatus newStatus, String changeReason, LocalDateTime changedAt,
                                            String changedBy, String tenantId) {
}
