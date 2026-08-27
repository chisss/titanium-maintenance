package com.titanium.maintenance.application.model;

import java.time.LocalDateTime;

/** 费用冲正应用输入。 */
public record MaintenanceReversalSettlementInput(
        String sourceAdjustmentId,
        LocalDateTime businessTime,
        String reason,
        String updatedBy) {
}
