package com.titanium.maintenance.port.payment;

import java.math.BigDecimal;

/**
 * 保单贷款出账端口（出口/driven port，g04-03）。
 * <p>
 * 保全域贷款案件生效出口的资金出账底座：表达对支付域「放款出账指令」的能力需求，由基础设施层
 * {@code LoanDisbursementPayoutAdapter} 经 Feign 调支付域放款出账契约实现，领域侧不依赖任何远程响应
 * 类型（防腐）。出账指令的权威在支付域，本域只发起并接收指令号回执。
 * </p>
 * <p>
 * 🔴 失败关闭契约：支付域应答业务码失败或应答体无效时，Adapter 抛
 * {@code PolicyLoanPayoutUnavailableException}；支付域不可达时抛 {@code MaintenanceRemoteCallException}
 * （{@code MAINTENANCE_PAYMENT_REMOTE_ERROR}，HTTP 502 语义）。调用方不得以 0 或空指令号兜底。
 * </p>
 */
public interface LoanDisbursementPayoutPort {

    /**
     * 发起保单贷款放款出账（同步等支付域返回出账指令号）。
     *
     * @param order 出账指令（贷款业务号与保单域放款明细同一幂等键）
     * @return 出账指令号回执；失败一律抛领域异常（失败关闭），绝不返回 null
     */
    LoanPayoutReceipt payOut(LoanPayoutOrder order);

    /**
     * 放款出账指令（保全域 → 支付域）。
     *
     * @param loanId 贷款明细业务号（与保单域放款明细同一幂等键）
     * @param policyId 保单ID
     * @param amount 放款本金
     * @param currency 币种（与保单币种一致）
     * @param customerId 收款客户ID（放款收款人）
     */
    record LoanPayoutOrder(
            String loanId,
            String policyId,
            BigDecimal amount,
            String currency,
            String customerId) {
    }

    /**
     * 放款出账回执（支付域 → 保全域）。
     *
     * @param paymentOrderId 出账指令号（支付域生成，权威）
     * @param loanId 贷款明细业务号（回显，与指令一致）
     */
    record LoanPayoutReceipt(
            String paymentOrderId,
            String loanId) {
    }
}
