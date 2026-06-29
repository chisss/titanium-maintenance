package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

import lombok.Builder;
import lombok.Getter;

/**
 * 执行保全命令（领域层）
 */
@Getter
@Builder
public class ExecuteMaintenanceCommand {
    private final MaintenanceId id;
    private final LocalDateTime effectiveTime;
    private final String        executionDetails;
    private final String        updatedBy;
}
