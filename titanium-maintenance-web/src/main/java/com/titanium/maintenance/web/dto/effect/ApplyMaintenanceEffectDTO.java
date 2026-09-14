package com.titanium.maintenance.web.dto.effect;

import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 人工/API 触发立即生效的请求。
 *
 * @param operationId      幂等操作号
 * @param investmentSwitch 账户转换案件的转换参数（非账户转换案件不传）
 */
public record ApplyMaintenanceEffectDTO(
        @NotBlank @Size(max = 128) String operationId,
        @Valid InvestmentSwitchDTO investmentSwitch) {

    /** 账户转换（FUND_SWITCH）参数：转出单位、目标净值、目标币种与标的。 */
    public record InvestmentSwitchDTO(
            @NotNull(message = "转出单位数不能为空")
            @DecimalMin(value = "0.0", inclusive = false, message = "转出单位数必须大于0")
            BigDecimal switchOutUnits,
            @NotNull(message = "转入净值不能为空")
            @DecimalMin(value = "0.0", inclusive = false, message = "转入净值必须大于0")
            BigDecimal targetUnitPrice,
            @Size(max = 8, message = "币种长度不能超过8")
            String targetCurrency,
            @NotBlank(message = "转入标的不能为空")
            @Size(max = 64, message = "转入标的长度不能超过64")
            String targetFund) {
    }
}
