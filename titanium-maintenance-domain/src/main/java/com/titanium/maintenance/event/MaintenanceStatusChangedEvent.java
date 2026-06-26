package com.titanium.maintenance.event;

import com.titanium.maintenance.enums.MaintenanceStatus;
import com.titanium.maintenance.valueobject.MaintenanceId;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
@AllArgsConstructor
public class MaintenanceStatusChangedEvent {
    private final MaintenanceId maintenanceId;
    private final MaintenanceStatus oldStatus;
    private final MaintenanceStatus newStatus;
    private final String changeReason;
    private final LocalDateTime changedAt;
    private final String changedBy;
    private final String tenantId;
}