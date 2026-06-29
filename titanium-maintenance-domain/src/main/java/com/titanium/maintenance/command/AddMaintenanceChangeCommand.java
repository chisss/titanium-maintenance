package com.titanium.maintenance.command;

import com.titanium.maintenance.enums.MaintenanceChangeType;
import com.titanium.maintenance.valueobject.MaintenanceId;

import lombok.Builder;
import lombok.Getter;

/**
 * 新增保全变更项命令（领域层）
 */
@Getter
@Builder
public class AddMaintenanceChangeCommand {
    private final MaintenanceId         id;
    private final MaintenanceChangeType changeType;
    private final String                fieldName;
    private final String                oldValue;
    private final String                newValue;
    private final String                createdBy;
}
