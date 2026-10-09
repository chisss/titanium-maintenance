package com.titanium.maintenance.port.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保单贷款还款收款端口（出口/driven port，g04-04）。
 * <p>
 * 保全域贷款还款案件生效出口的资金入账底座：还款是<b>向客户收款</b>（与放款出账方向相反），正落在支付域
 * 既有支付单契约（{@code POST /api/v1/payments}）语义内，故复用而不另建收款契约；由基础设施层
 * {@code LoanRepaymentCollectionAdapter} 经 Feign 实现。收款事实的权威在支付域，本域只发起并接收回执。
 * </p>
 * <p>
 * 🔴 失败关闭契约：支付域应答业务码失败或应答体无效时，Adapter 抛
 * {@code LoanRepaymentCollectionUnavailableException}；支付域不可达时抛 {@code MaintenanceRemoteCallException}
 * （{@code MAINTENANCE_PAYMENT_REMOTE_ERROR}，HTTP 502 语义）。调用方不得以 0 或空指令号兜底。
 * </p>
 */
public interface LoanRepaymentCollectionPort {

    /**
     * 发起保单贷款还款收款（同步等支付域返回收款单号）。
     *
     * @param order 收款指令（还款业务号作为支付单业务号，即幂等键）
     * @return 收款回执；失败一律抛领域异常（失败关闭），绝不返回 null
     */
    RepaymentCollectionReceipt collect(RepaymentCollectionOrder order);

    /**
     * 还款收款指令（保全域 → 支付域）。
     *
     * @param repaymentId 还款业务号（保全域稳定派生，作为支付单业务号 = 幂等键）
     * @param policyId 保单ID
     * @param customerId 付款客户ID（还款人）
     * @param amount 还款总额（与保单域记账同一金额）
     * @param currency 币种（与保单币种一致）
     * @param description 收款摘要（业务文案，调用方以枚举化文案传入）
     */
    record RepaymentCollectionOrder(
            String repaymentId,
            String policyId,
            String customerId,
            BigDecimal amount,
            String currency,
            String description) {
    }

    /**
     * 还款收款回执（支付域 → 保全域）。
     *
     * @param orderId 收款单业务号（回显，与指令的还款业务号一致）
     * @param policyId 保单ID（回显，与指令一致）
     * @param status 支付单状态（支付域权威）
     * @param transactionId 第三方交易号（可空：尚未完成渠道交易时为空）
     * @param paymentDate 支付完成时间（可空，同上）
     */
    record RepaymentCollectionReceipt(
            String orderId,
            String policyId,
            String status,
            String transactionId,
            LocalDateTime paymentDate) {
    }
}
