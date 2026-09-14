package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.item.MaintenanceItemSelectionEvidence;

/** 向保全案件添加一个版本化保全项。 */
public record AddMaintenanceItemCommand(@TargetAggregateIdentifier MaintenanceId id,
        MaintenanceItemDefinition definition, MaintenanceItemSelectionEvidence selectionEvidence,
        String createdBy,
        String tenantId) {

    /** 兼容旧调用；独立建案初始化不会接受该构造器产生的非权威证据。 */
    public AddMaintenanceItemCommand(
            MaintenanceId id, MaintenanceItemDefinition definition, String createdBy, String tenantId) {
        this(id, definition, null, createdBy, tenantId);
    }
}
