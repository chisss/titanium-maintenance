package com.titanium.maintenance.api.request;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 发起退保现金价值、账务贷项和资金退款的请求。 */
public record SettleMaintenanceSurrenderRequest(
        @NotBlank String originalCalculationId,
        @NotNull LocalDate surrenderDate,
        @NotNull @Min(1) Integer policyYear,
        @NotNull LocalDateTime businessTime,
        @NotBlank String reason,
        @NotBlank String updatedBy) {
}
