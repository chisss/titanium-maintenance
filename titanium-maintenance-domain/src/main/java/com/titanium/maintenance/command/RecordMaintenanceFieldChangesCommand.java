package com.titanium.maintenance.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;

/** 保存某一保全项的完整字段变更提案。 */
public record RecordMaintenanceFieldChangesCommand(@TargetAggregateIdentifier MaintenanceId id,
        String itemCode, List<MaintenanceFieldChange> changes, String updatedBy) {
}
