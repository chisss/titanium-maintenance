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
 * @param loanDisbursement 保单贷款放款案件的申请金额参数（非放款案件不传）
 * @param loanRepayment    保单贷款还款案件的目标明细与还款额参数（非还款案件不传）
 */
public record ApplyMaintenanceEffectDTO(
        @NotBlank @Size(max = 128) String operationId,
        @Valid InvestmentSwitchDTO investmentSwitch,
        @Valid LoanDisbursementDTO loanDisbursement,
        @Valid LoanRepaymentDTO loanRepayment) {

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

    /** 保单贷款（POLICY_LOAN）参数：申请贷款金额（是否超可贷上限由领域服务核定，与投资参数互斥）。 */
    public record LoanDisbursementDTO(
            @NotNull(message = "申请贷款金额不能为空")
            @DecimalMin(value = "0.0", inclusive = false, message = "申请贷款金额必须大于0")
            BigDecimal loanAmount) {
    }

    /**
     * 保单贷款还款（LOAN_REPAYMENT）参数：目标贷款明细业务号与还款额。
     * <p>
     * 🔴 目标明细由调用方<b>显式指名</b>，不做「按保单查唯一未结清贷款」的隐式推断——同一保单可先后多笔贷款，
     * 部分还款时「还哪笔」是业务事实；指名错误由保单域以「贷款明细不存在」失败关闭。
     * 是否超欠款总额、利息/本金如何拆分均由保单域权威核定，本层只传原始要素。
     * </p>
     */
    public record LoanRepaymentDTO(
            @NotBlank(message = "目标贷款明细业务号不能为空")
            @Size(max = 32, message = "目标贷款明细业务号长度不能超过32")
            String loanId,
            @NotNull(message = "还款金额不能为空")
            @DecimalMin(value = "0.0", inclusive = false, message = "还款金额必须大于0")
            BigDecimal repaymentAmount) {
    }
}
