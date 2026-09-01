package com.titanium.maintenance.application.command.premium;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

/** 触发费用任务 Billing 与 Payment 门禁的应用层输入。 */
public record MaintenancePremiumSettlementGateInput(
        String maintenanceId,
        String taskId,
        String operationId,
        String paymentMethod,
        String reason,
        String operatorId,
        String tenantId,
        MaintenanceChannel source) {
}
