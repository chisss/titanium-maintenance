package com.titanium.maintenance.command;

import com.titanium.maintenance.enums.MaintenanceStatus;
import com.titanium.maintenance.valueobject.MaintenanceId;
import lombok.Builder;
import lombok.Value;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 变更保全状态命令（领域层）
 */
@Value
@Builder
public class ChangeMaintenanceStatusCommand {
    @TargetAggregateIdentifier
    MaintenanceId id;
    MaintenanceStatus newStatus;
    String changeReason;
    String changedBy;

    // 静态工厂方法，方便创建命令
    public static ChangeMaintenanceStatusCommand of(String maintenanceId, MaintenanceStatus newStatus,
                                                  String changeReason, String changedBy) {
        return ChangeMaintenanceStatusCommand.builder()
                .id(MaintenanceId.of(maintenanceId))
                .newStatus(newStatus)
                .changeReason(changeReason)
                .changedBy(changedBy)
                .build();
    }
}
