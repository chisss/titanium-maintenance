package com.titanium.maintenance.valueobject;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceChangeType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public class MaintenanceChange {
    private final MaintenanceChangeType changeType;
    private final String                fieldName;
    private final String                oldValue;
    private final String                newValue;
    private final LocalDateTime         createdAt;
}
