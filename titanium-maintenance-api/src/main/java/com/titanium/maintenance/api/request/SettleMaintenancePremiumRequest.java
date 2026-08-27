package com.titanium.maintenance.api.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 发起保全替代计算和客户余额登记的请求。 */
public record SettleMaintenancePremiumRequest(
        @NotBlank String originalCalculationId,
        @NotBlank String productId,
        @NotBlank String productVersion,
        @NotNull LocalDateTime businessTime,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal sumInsured,
        @NotNull @Min(0) @Max(120) Integer age,
        @NotBlank String gender,
        @NotNull @Min(1) Integer paymentTermYears,
        @NotNull @Min(1) Integer coverageTermYears,
        @NotNull @Min(1) Integer paymentPeriods,
        Map<String, Object> requestSnapshot,
        @Valid List<UnderwritingAdjustment> underwritingAdjustments,
        String channelId,
        @Min(1) Integer policyYear,
        @NotBlank String reason,
        @NotBlank String updatedBy) {

    /** 保全重算沿用的结构化核保调整。 */
    public record UnderwritingAdjustment(
            @NotBlank String adjustmentCode,
            @NotBlank String type,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal value,
            String reason,
            String ruleVersion) {
    }
}
