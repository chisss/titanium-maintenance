package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawal;

/** 项目撤销补偿已记录；完成时同时携带重建后的当前拟快照。 */
public record MaintenanceItemWithdrawalCompensationRecordedEvent(
        MaintenanceId maintenanceId,
        MaintenanceItemWithdrawal withdrawal,
        MaintenanceFieldConflictPlan proposedPlan,
        String operatedBy,
        String tenantId) {
}
