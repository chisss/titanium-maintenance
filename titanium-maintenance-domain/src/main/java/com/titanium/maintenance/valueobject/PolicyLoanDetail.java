package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 保单贷款明细值对象
 * <p>
 * 承载保单贷款（以保单现金价值为质押向保险公司借款）计算所需领域数据：现金价值、可贷比例、
 * 已有未偿贷款本息、申请贷款金额、贷款年利率。贷款额度不变量（申请额不得超过可贷上限）与利息
 * 计算内聚于本值对象（充血模型），具体放款决策由领域服务/编排承载。
 * </p>
 *
 * @param cashValue 保单现金价值
 * @param loanableRatio 可贷比例（如 0.8 表示最高可贷现金价值的 80%）
 * @param outstandingPrincipal 已有未偿贷款本金（无则为 0）
 * @param outstandingInterest 已有未偿贷款利息（无则为 0）
 * @param requestedAmount 本次申请贷款金额
 * @param annualInterestRate 贷款年利率（如 0.05 表示 5%）
 */
public record PolicyLoanDetail(BigDecimal cashValue, BigDecimal loanableRatio, BigDecimal outstandingPrincipal,
                               BigDecimal outstandingInterest, BigDecimal requestedAmount,
                               BigDecimal annualInterestRate) {

    public PolicyLoanDetail {
        if (cashValue == null || cashValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("现金价值不能为空或负数");
        }
        if (loanableRatio == null || loanableRatio.compareTo(BigDecimal.ZERO) <= 0
                || loanableRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("可贷比例须在 (0,1] 区间");
        }
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("申请贷款金额必须大于零");
        }
        if (annualInterestRate == null || annualInterestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("贷款年利率不能为负");
        }
        outstandingPrincipal = outstandingPrincipal == null ? BigDecimal.ZERO : outstandingPrincipal;
        outstandingInterest = outstandingInterest == null ? BigDecimal.ZERO : outstandingInterest;
    }

    /**
     * 可贷款上限 = 现金价值 × 可贷比例 − 已有未偿本息。
     *
     * @return 本次最多可贷金额（不为负）
     */
    public BigDecimal maxLoanableAmount() {
        BigDecimal cap = cashValue.multiply(loanableRatio)
                .subtract(outstandingPrincipal).subtract(outstandingInterest)
                .setScale(2, RoundingMode.HALF_UP);
        return cap.compareTo(BigDecimal.ZERO) > 0 ? cap : BigDecimal.ZERO;
    }

    /**
     * 申请贷款金额是否在可贷上限内。
     *
     * @return 未超上限返回 {@code true}
     */
    public boolean isWithinLoanableLimit() {
        return requestedAmount.compareTo(maxLoanableAmount()) <= 0;
    }

    /**
     * 放款后累计贷款本金 = 已有未偿本金 + 本次申请金额。
     *
     * @return 累计贷款本金
     */
    public BigDecimal totalPrincipalAfterLoan() {
        return outstandingPrincipal.add(requestedAmount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算指定天数的贷款利息（按年利率单利日计：本金 × 年利率 × 天数/365）。
     *
     * @param days 计息天数
     * @return 应计利息
     */
    public BigDecimal accruedInterest(int days) {
        if (days <= 0) {
            return BigDecimal.ZERO;
        }
        return totalPrincipalAfterLoan().multiply(annualInterestRate)
                .multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);
    }
}
