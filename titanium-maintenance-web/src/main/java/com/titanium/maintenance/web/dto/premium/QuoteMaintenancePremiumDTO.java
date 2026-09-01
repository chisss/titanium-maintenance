package com.titanium.maintenance.web.dto.premium;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 触发 Product 保全报价的定价输入；不接受调用方提交最终金额。 */
public record QuoteMaintenancePremiumDTO(
        @NotBlank @Size(max = 128) String operationId,
        @Size(max = 32) String lifecycleType,
        @Size(max = 128) String originalCalculationId,
        @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @DecimalMin(value = "0", inclusive = false) BigDecimal sumInsured,
        @Min(0) @Max(120) Integer age,
        @Size(max = 32) String gender,
        @Min(1) Integer paymentTermYears,
        @Min(1) Integer coverageTermYears,
        @Min(1) Integer paymentPeriods,
        Map<String, Object> pricingFactors,
        @Valid List<UnderwritingAdjustmentDTO> underwritingAdjustments,
        @Size(max = 64) String channelId,
        @Min(1) Integer policyYear,
        @NotBlank @Size(max = 500) String reason) {

    public QuoteMaintenancePremiumDTO {
        pricingFactors = pricingFactors == null ? Map.of() : Map.copyOf(pricingFactors);
        underwritingAdjustments = underwritingAdjustments == null
                ? List.of()
                : List.copyOf(underwritingAdjustments);
    }

    /** 拒绝金额、方向、报价版本等由 Product 决定的未声明字段。 */
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("保全报价请求不支持字段: " + fieldName);
    }

    /** 核保调费输入，不含 Product 最终计算结果。 */
    public record UnderwritingAdjustmentDTO(
            @NotBlank @Size(max = 64) String adjustmentCode,
            @NotBlank @Size(max = 32) String type,
            @DecimalMin(value = "0") BigDecimal value,
            @Size(max = 500) String reason,
            @Size(max = 64) String ruleVersion) {
    }
}
