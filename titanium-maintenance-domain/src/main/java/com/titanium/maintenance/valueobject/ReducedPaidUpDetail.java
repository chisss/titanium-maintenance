package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 减额缴清明细值对象
 * <p>
 * 承载减额缴清（投保人不再缴费，以保单当前现金价值作为一次性趸缴净保费，购买一份保额减少的「缴清」
 * 保单，保障持续至原保险期间届满）计算所需领域数据：当前现金价值、原保额、每元保额趸缴净费率。
 * 减额后新保额 = 现金价值 / 每元保额趸缴净费率，计算内聚于本值对象（充血模型）。
 * </p>
 *
 * @param cashValue 保单当前现金价值（作为趸缴净保费）
 * @param originalSumInsured 原基本保额
 * @param netSinglePremiumRatePerUnit 每元保额的趸缴净保费率（按被保人当前年龄/剩余保障期精算得出）
 */
public record ReducedPaidUpDetail(BigDecimal cashValue, BigDecimal originalSumInsured,
                                  BigDecimal netSinglePremiumRatePerUnit) {

    public ReducedPaidUpDetail {
        if (cashValue == null || cashValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("现金价值必须大于零方可减额缴清");
        }
        if (originalSumInsured == null || originalSumInsured.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("原保额必须大于零");
        }
        if (netSinglePremiumRatePerUnit == null || netSinglePremiumRatePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("每元保额趸缴净费率必须大于零");
        }
    }

    /**
     * 减额缴清后的新保额 = 现金价值 / 每元保额趸缴净费率。
     * <p>
     * 因不再缴费，新保额通常低于原保额（故称「减额」），保障持续至原保险期间届满。
     * </p>
     *
     * @return 减额后新保额
     */
    public BigDecimal reducedSumInsured() {
        return cashValue.divide(netSinglePremiumRatePerUnit, 2, RoundingMode.HALF_UP);
    }

    /**
     * 减额后新保额是否确实不超过原保额（减额缴清的自洽性校验）。
     *
     * @return 新保额 ≤ 原保额返回 {@code true}
     */
    public boolean isReductionValid() {
        return reducedSumInsured().compareTo(originalSumInsured) <= 0;
    }
}
