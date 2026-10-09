package com.titanium.maintenance.port.billing;

import java.math.BigDecimal;

/**
 * 保单贷款还款冲销入账端口（出口/driven port，g04-04）。
 * <p>
 * 保全域贷款还款案件生效出口的账务冲减底座：表达对计费域「贷款还款入账」的能力需求，由基础设施层
 * {@code PolicyLoanRepaymentPostingAdapter} 经 Feign 调计费域还款入账契约实现，领域侧不依赖任何远程响应
 * 类型（防腐）。入账流水的权威在计费域，本域只发起并接收流水号回执。
 * </p>
 * <p>
 * 🔴 <b>拆分原样承接、不复算</b>：利息/本金拆分取自保单域还款回执（唯一权威），本端口只负责原样转交，
 * 计费域另以「利息 + 本金 == 还款总额」勾稽兜底。调用方不得在此重算分配。
 * </p>
 * <p>
 * 🔴 失败关闭契约：计费域应答业务码失败或应答体无效时，Adapter 抛
 * {@code PolicyLoanRepaymentPostingUnavailableException}；计费域不可达时抛 {@code MaintenanceRemoteCallException}
 * （{@code MAINTENANCE_BILLING_REMOTE_ERROR}，HTTP 502 语义）。调用方不得以 0 或空流水号兜底。
 * </p>
 */
public interface PolicyLoanRepaymentPostingPort {

    /**
     * 记录保单贷款还款入账（同步等计费域返回流水号）。
     *
     * @param order 入账指令（还款业务号与保单域还款明细同一幂等键）
     * @return 入账流水号回执；失败一律抛领域异常（失败关闭），绝不返回 null
     */
    PolicyLoanRepaymentPostingReceipt postLoanRepayment(PolicyLoanRepaymentPostingOrder order);

    /**
     * 还款入账指令（保全域 → 计费域）。
     *
     * @param repaymentId 还款业务号（与保单域还款明细同一幂等键）
     * @param loanId 被冲减的贷款明细业务号
     * @param policyId 保单ID
     * @param amount 还款总额
     * @param interestPortion 其中利息部分（保单域权威分配）
     * @param principalPortion 其中本金部分（保单域权威分配）
     * @param currency 币种（与保单币种一致）
     * @param createdBy 创建人（入账事实落库操作人，取自还款回执命令载荷）
     * @param tenantId 租户ID（计费域契约请求体必填，须与请求头租户一致）
     */
    record PolicyLoanRepaymentPostingOrder(
            String repaymentId,
            String loanId,
            String policyId,
            BigDecimal amount,
            BigDecimal interestPortion,
            BigDecimal principalPortion,
            String currency,
            String createdBy,
            String tenantId) {
    }

    /**
     * 还款入账回执（计费域 → 保全域）。
     *
     * @param postingId 入账流水号（计费域生成，权威）
     * @param repaymentId 还款业务号（回显，与指令一致）
     */
    record PolicyLoanRepaymentPostingReceipt(
            String postingId,
            String repaymentId) {
    }
}
