package com.titanium.maintenance.port.billing;

import java.math.BigDecimal;

/**
 * 保单贷款入账端口（出口/driven port，g04-03）。
 * <p>
 * 保全域贷款案件生效出口的账务写入底座：表达对计费域「贷款负债入账」的能力需求，由基础设施层
 * {@code PolicyLoanPostingAdapter} 经 Feign 调计费域贷款入账契约实现，领域侧不依赖任何远程响应类型
 * （防腐）。入账流水的权威在计费域，本域只发起并接收流水号回执。
 * </p>
 * <p>
 * 🔴 失败关闭契约：计费域应答业务码失败或应答体无效时，Adapter 抛
 * {@code PolicyLoanPostingUnavailableException}；计费域不可达时抛 {@code MaintenanceRemoteCallException}
 * （{@code MAINTENANCE_BILLING_REMOTE_ERROR}，HTTP 502 语义）。调用方不得以 0 或空流水号兜底。
 * </p>
 */
public interface PolicyLoanPostingPort {

    /**
     * 记录保单贷款放款入账（同步等计费域返回流水号）。
     *
     * @param order 入账指令（贷款业务号与保单域放款明细同一幂等键）
     * @return 入账流水号回执；失败一律抛领域异常（失败关闭），绝不返回 null
     */
    PolicyLoanPostingReceipt postLoanDisbursement(PolicyLoanPostingOrder order);

    /**
     * 贷款入账指令（保全域 → 计费域）。
     *
     * @param loanId 贷款明细业务号（与保单域放款明细同一幂等键）
     * @param policyId 保单ID
     * @param amount 放款本金
     * @param currency 币种（与保单币种一致）
     * @param createdBy 创建人（入账事实落库操作人，取自放款回执命令载荷）
     * @param tenantId 租户ID（计费域契约请求体必填，须与请求头租户一致）
     */
    record PolicyLoanPostingOrder(
            String loanId,
            String policyId,
            BigDecimal amount,
            String currency,
            String createdBy,
            String tenantId) {
    }

    /**
     * 贷款入账回执（计费域 → 保全域）。
     *
     * @param postingId 入账流水号（计费域生成，权威）
     * @param loanId 贷款明细业务号（回显，与指令一致）
     */
    record PolicyLoanPostingReceipt(
            String postingId,
            String loanId) {
    }
}
