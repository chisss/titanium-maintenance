package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
@AllArgsConstructor
public class MaintenanceExecutedEvent {
    private final MaintenanceId maintenanceId;
    private final LocalDateTime effectiveTime;
    private final String executionDetails;
    private final LocalDateTime updatedAt;
    private final String updatedBy;
    private final String tenantId;
}