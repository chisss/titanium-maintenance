package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.valueobject.MaintenanceId;

/**
 * 变更保全状态命令（领域层）
 */
public record ChangeMaintenanceStatusCommand(@TargetAggregateIdentifier MaintenanceId id, MaintenanceStatus newStatus,
        String changeReason, String changedBy) {

    /**
     * 静态工厂：从外部原始参数构造命令。
     */
    public static ChangeMaintenanceStatusCommand of(String maintenanceId, MaintenanceStatus newStatus,
            String changeReason, String changedBy) {
        return new ChangeMaintenanceStatusCommand(MaintenanceId.of(maintenanceId), newStatus, changeReason, changedBy);
    }
}
