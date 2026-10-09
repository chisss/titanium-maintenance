package com.titanium.maintenance.application.model.effect;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanRepaymentReceipt;
import com.titanium.maintenance.valueobject.workflow.MaintenanceLoanRepaymentEvidence;

/**
 * 保单贷款还款事实（application 层编排出参，g04-04）。
 * <p>
 * 还款三连写全部成功后的要素集合：利息/本金拆分、本段计息、还款后余额一律取自<b>保单域回执</b>
 * （唯一权威，幂等重试时回显首次记账事实），入账流水号与收款指令号取自计费域/支付域回执。
 * </p>
 * <p>
 * 🔴 <b>组装内聚在 {@link #toEvidence(String, LocalDateTime)}</b>：生效回执证据有 15 个分量，
 * 散在编排器里内联构造既难读也易漏字段（漏一个即留痕残缺，且因回执自洽而无从察觉），故由本类型
 * 承担「事实 → 证据」的唯一翻译。
 * </p>
 *
 * @param repaymentId 还款业务号（稳定派生幂等键）
 * @param loanId 目标贷款明细业务号
 * @param policyId 保单ID
 * @param repaymentAmount 还款总额（保单域回执回显）
 * @param interestPortion 本次冲减的利息部分（保单域回执权威分配）
 * @param principalPortion 本次冲减的本金部分（保单域回执权威分配）
 * @param accruedInterest 本次计息区间增量利息（保单域回执权威计算，单利 365）
 * @param outstandingPrincipalAfter 还款后未偿本金（保单域回执权威余额）
 * @param outstandingInterestAfter 还款后已计未付利息（保单域回执权威余额）
 * @param settled 是否已结清（保单域回执权威判定）
 * @param repaymentDate 还款日期（保单域回执回显，首次记账为准）
 * @param billingPostingId 计费域入账流水号
 * @param paymentOrderId 支付域收款单号
 */
public record LoanRepaymentFact(
        String repaymentId,
        String loanId,
        String policyId,
        BigDecimal repaymentAmount,
        BigDecimal interestPortion,
        BigDecimal principalPortion,
        BigDecimal accruedInterest,
        BigDecimal outstandingPrincipalAfter,
        BigDecimal outstandingInterestAfter,
        boolean settled,
        LocalDateTime repaymentDate,
        String billingPostingId,
        String paymentOrderId) {

    /**
     * 保单域还款回执 + 计费/支付回执 → 还款事实。
     * <p>
     * 🔴 <b>分配与余额只从回执取名，不接受任何入参覆盖</b>：13 个分量中 11 个直接来自保单域回执（对端
     * 权威），另 2 个是两个下游回执号。位置参数多且含连续 5 个金额字段，散在编排器里内联构造极易错位
     * （错位后回执自洽、无从察觉），故由本工厂承担唯一翻译。
     * </p>
     *
     * @param receipt 保单域还款回执（权威分配与余额）
     * @param billingPostingId 计费域入账流水号
     * @param paymentOrderId 支付域收款单号
     */
    public static LoanRepaymentFact of(PolicyLoanRepaymentReceipt receipt,
            String billingPostingId, String paymentOrderId) {
        return new LoanRepaymentFact(
                receipt.repaymentId(), receipt.loanId(), receipt.policyId(), receipt.repaymentAmount(),
                receipt.interestPortion(), receipt.principalPortion(), receipt.accruedInterest(),
                receipt.outstandingPrincipalAfter(), receipt.outstandingInterestAfter(), receipt.settled(),
                receipt.repaymentDate(), billingPostingId, paymentOrderId);
    }

    /**
     * 事实 → 案件级生效回执证据。
     *
     * @param requestId 生效请求号（案件级请求证据的标识，与还款业务号同为幂等勾稽键）
     * @param repaidAt 本域收到还款回执的时刻（不参与载荷摘要，见证据 {@code contentHash()} 说明）
     * @return 可持久化到事件流的还款回执证据
     */
    public MaintenanceLoanRepaymentEvidence toEvidence(String requestId, LocalDateTime repaidAt) {
        return new MaintenanceLoanRepaymentEvidence(
                requestId, repaymentId, loanId, policyId, repaymentAmount, interestPortion,
                principalPortion, accruedInterest, outstandingPrincipalAfter, outstandingInterestAfter,
                settled, repaymentDate, billingPostingId, paymentOrderId, repaidAt);
    }
}
