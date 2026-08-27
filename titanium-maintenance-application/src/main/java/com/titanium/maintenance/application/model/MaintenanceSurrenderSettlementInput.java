package com.titanium.maintenance.application.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 退保价值结算应用输入。 */
public record MaintenanceSurrenderSettlementInput(
        String originalCalculationId,
        LocalDate surrenderDate,
        Integer policyYear,
        LocalDateTime businessTime,
        String reason,
        String updatedBy) {
}
