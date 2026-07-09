package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.titanium.maintenance.common.enums.RefundType;

/**
 * 退保明细值对象
 * <p>
 * 承载退保退费计算所需的领域数据：已缴保费、保单现金价值、保单生效日、犹豫期天数、退保申请日。
 * 退费金额的具体计算规则内聚在领域服务 {@code SurrenderRefundDomainService}，本值对象仅提供
 * 「是否在犹豫期内」这一自身可判定的不变量。
 * </p>
 *
 * @param premiumPaid 已缴保费总额
 * @param cashValue 保单现金价值（犹豫期外退保退还此值）
 * @param policyEffectiveDate 保单生效日
 * @param coolingOffDays 犹豫期天数（通常 10~15 天）
 * @param surrenderApplyDate 退保申请日
 */
public record SurrenderDetail(BigDecimal premiumPaid, BigDecimal cashValue, LocalDate policyEffectiveDate,
                              int coolingOffDays, LocalDate surrenderApplyDate) {

    public SurrenderDetail {
        if (premiumPaid == null || premiumPaid.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("已缴保费不能为空或负数");
        }
        if (cashValue == null || cashValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("现金价值不能为空或负数");
        }
        if (policyEffectiveDate == null || surrenderApplyDate == null) {
            throw new IllegalArgumentException("保单生效日与退保申请日不能为空");
        }
    }

    /**
     * 是否在犹豫期内：退保申请日 ≤ 生效日 + 犹豫期天数。
     *
     * @return true 表示犹豫期内退保
     */
    public boolean isWithinCoolingOff() {
        LocalDate coolingOffEnd = policyEffectiveDate.plusDays(coolingOffDays);
        return !surrenderApplyDate.isAfter(coolingOffEnd);
    }

    /**
     * 适用的退费类型：犹豫期内全额退，期外扣现金价值。
     *
     * @return 退费类型
     */
    public RefundType applicableRefundType() {
        return isWithinCoolingOff() ? RefundType.FULL_REFUND : RefundType.DEDUCT_CASH_VALUE;
    }
}
