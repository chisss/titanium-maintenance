package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;

/**
 * 保单贷款计算结果值对象
 *
 * @param approved 是否核准放款（申请额在可贷上限内）
 * @param loanAmount 实际放款金额（核准时为申请额，拒绝时为 0）
 * @param maxLoanableAmount 可贷款上限
 * @param totalPrincipalAfterLoan 放款后累计贷款本金
 * @param rejectReason 拒绝原因（核准时为 null）
 */
public record PolicyLoanResult(boolean approved, BigDecimal loanAmount, BigDecimal maxLoanableAmount,
                               BigDecimal totalPrincipalAfterLoan, String rejectReason) {
}
