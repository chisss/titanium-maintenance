package com.titanium.maintenance.web.dto.premium;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 费用任务结算请求；方向、金额和币种均来自 Product 报价。 */
public record SettleMaintenancePremiumDTO(
        @NotBlank @Size(max = 128) String operationId,
        @Size(max = 32) String paymentMethod,
        @NotBlank @Size(max = 500) String reason) {

    /** 拒绝金额、方向和币种等由 Product 报价与 Billing 入账决定的字段。 */
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("保全结算请求不支持字段: " + fieldName);
    }
}
