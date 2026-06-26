package com.titanium.maintenance.event;

import com.titanium.maintenance.enums.MaintenanceChangeType;
import com.titanium.maintenance.valueobject.MaintenanceId;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
@AllArgsConstructor
public class MaintenanceChangeAddedEvent {
    private final MaintenanceId maintenanceId;
    private final MaintenanceChangeType changeType;
    private final String fieldName;
    private final String oldValue;
    private final String newValue;
    private final LocalDateTime createdAt;
    private final String createdBy;
    private final String tenantId;
}