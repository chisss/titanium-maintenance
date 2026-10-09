package com.titanium.maintenance.application.model.effect;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保单贷款放款事实（application 层编排出参，g04-03）。
 * <p>
 * 放款三连写全部成功后的要素集合：本金/利率/放款日期一律取自保单域回执回显（幂等重试时回显首次
 * 入账事实），入账流水号与出账指令号取自计费域/支付域回执。供生效编排组装
 * {@code MaintenanceLoanDisbursementEvidence} 权威回执。
 * </p>
 *
 * @param loanId 贷款明细业务号（稳定派生幂等键）
 * @param policyId 保单ID
 * @param loanAmount 放款本金（保单域回执回显）
 * @param interestRate 冻结利率（保单域回执回显，首次入账为准）
 * @param loanDate 放款日期（保单域回执回显，首次入账为准）
 * @param billingPostingId 计费域入账流水号
 * @param paymentOrderId 支付域出账指令号
 */
public record LoanDisbursementFact(
        String loanId,
        String policyId,
        BigDecimal loanAmount,
        BigDecimal interestRate,
        LocalDateTime loanDate,
        String billingPostingId,
        String paymentOrderId) {
}
