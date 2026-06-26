package com.titanium.maintenance.valueobject;

import com.titanium.maintenance.enums.MaintenanceChangeType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
@AllArgsConstructor
public class MaintenanceChange {
    private final MaintenanceChangeType changeType;
    private final String fieldName;
    private final String oldValue;
    private final String newValue;
    private final LocalDateTime createdAt;
}