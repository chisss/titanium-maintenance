package com.titanium.maintenance.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.MaintenanceId;

/** 确认独立建案的全部保全项已经成功冻结。 */
public record CompleteMaintenanceCaseInitializationCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        List<String> itemCodes,
        String completedBy) {

    public CompleteMaintenanceCaseInitializationCommand {
        if (id == null || itemCodes == null || itemCodes.isEmpty()
                || itemCodes.stream().anyMatch(code -> code == null || code.isBlank())
                || completedBy == null || completedBy.isBlank()) {
            throw new MaintenanceValidationException(
                    "CompleteMaintenanceCaseInitializationCommand", "初始化完成参数不完整");
        }
        itemCodes = List.copyOf(itemCodes);
        completedBy = completedBy.trim();
    }
}
