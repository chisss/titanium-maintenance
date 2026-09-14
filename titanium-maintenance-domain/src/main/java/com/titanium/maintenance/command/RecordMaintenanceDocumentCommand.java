package com.titanium.maintenance.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceDocumentEvidence;

/** 出具保全凭证并完成凭证步骤（模板编码以案件冻结配置为准，不接受调用方自报）。 */
public record RecordMaintenanceDocumentCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        String taskId,
        String operationId,
        MaintenanceDocumentEvidence evidence,
        String operatorId,
        String tenantId) {
}
