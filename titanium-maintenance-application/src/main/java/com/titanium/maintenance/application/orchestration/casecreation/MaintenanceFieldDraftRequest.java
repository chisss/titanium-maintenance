package com.titanium.maintenance.application.orchestration.casecreation;

import java.util.List;

import com.titanium.maintenance.application.command.field.RecordMaintenanceFieldChangesInput.FieldProposalInput;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.item.MaintenanceItemCode;

/** 保存独立案件字段草稿的应用层输入。 */
public record MaintenanceFieldDraftRequest(
        String maintenanceId,
        String itemCode,
        List<FieldProposalInput> proposals,
        String operatorId,
        String tenantId) {

    public MaintenanceFieldDraftRequest {
        maintenanceId = requireText("maintenanceId", maintenanceId);
        itemCode = MaintenanceItemCode.of(itemCode).value();
        operatorId = requireText("operatorId", operatorId);
        tenantId = requireText("tenantId", tenantId);
        if (proposals == null || proposals.isEmpty() || proposals.size() > 100) {
            throw validation("proposals", "字段提案数量必须为1到100");
        }
        if (proposals.stream().anyMatch(proposal -> proposal == null)) {
            throw validation("proposals", "字段提案不能包含空项");
        }
        proposals = List.copyOf(proposals);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceFieldDraftRequest", fieldName, message);
    }
}
