package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawal;

/** 项目撤销外部编排失败，保留为同操作可重试状态。 */
public record MaintenanceItemWithdrawalFailedEvent(
        MaintenanceId maintenanceId,
        MaintenanceItemWithdrawal withdrawal,
        String operatedBy,
        String tenantId) {
}
