package com.titanium.maintenance.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldCatalogSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldProposal;

/** 使用当前 Policy 与字段目录权威证据保存一个项目的完整字段提案。 */
public record ProposeMaintenanceFieldChangesCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String itemCode,
        PolicyMaintenanceSnapshot currentPolicySnapshot,
        List<MaintenanceFieldProposal> proposals,
        MaintenanceFieldCatalogSnapshot fieldCatalogSnapshot,
        String updatedBy,
        String tenantId) {

    public ProposeMaintenanceFieldChangesCommand {
        proposals = proposals == null ? List.of() : List.copyOf(proposals);
    }
}
