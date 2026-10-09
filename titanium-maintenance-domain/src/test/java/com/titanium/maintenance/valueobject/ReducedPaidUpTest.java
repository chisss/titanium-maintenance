package com.titanium.maintenance.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 减额缴清核定值对象测试（g04-05 AC-06）
 * <p>
 * 覆盖「新保额 = 现金价值 ÷ 每元保额趸缴净费率」的计算口径与舍入，以及「新保额 ≤ 原保额」的减额自洽性判据。
 * 值对象可 {@code new} 直测，不启动容器。
 * </p>
 * <p>
 * 🔴 与 71007012（{@code MAINTENANCE_REDUCED_PAID_UP_EXCEEDS_SUM_INSURED}）的分工：本值对象只<b>判定</b>
 * 减额是否成立（{@link ReducedPaidUpDetail#isReductionValid()}）；把它变成携错误码的拒绝，是 application 层
 * {@code ReducedPaidUpOrchestrator} 的职责（该拒绝路径由应用层用例覆盖）。此处若也断言 71007012，
 * 等于在值对象上测一个它没有的行为。
 * </p>
 */
class ReducedPaidUpTest {

    @Test
    @DisplayName("正例：新保额 = 现金价值 ÷ 每元保额趸缴净费率")
    void shouldComputeReducedSumInsured() {
        ReducedPaidUpDetail detail = new ReducedPaidUpDetail(
                new BigDecimal("30000"), new BigDecimal("200000"), new BigDecimal("0.3"));

        assertEquals(new BigDecimal("100000.00"), detail.reducedSumInsured());
        assertTrue(detail.isReductionValid(), "新保额10万 ≤ 原保额20万，减额成立");
    }

    @Test
    @DisplayName("正例：除不尽时按 HALF_UP 保留两位小数")
    void shouldRoundHalfUpToTwoDecimals() {
        ReducedPaidUpDetail detail = new ReducedPaidUpDetail(
                new BigDecimal("10000"), new BigDecimal("200000"), new BigDecimal("0.3"));

        // 10000 / 0.3 = 33333.333... → 33333.33
        assertEquals(new BigDecimal("33333.33"), detail.reducedSumInsured());
        assertTrue(detail.isReductionValid());
    }

    @Test
    @DisplayName("边界：新保额恰好等于原保额 → 仍视为减额成立（取 ≤ 而非 <）")
    void shouldTreatEqualToOriginalAsValid() {
        ReducedPaidUpDetail detail = new ReducedPaidUpDetail(
                new BigDecimal("20000"), new BigDecimal("40000"), new BigDecimal("0.5"));

        assertEquals(new BigDecimal("40000.00"), detail.reducedSumInsured());
        assertTrue(detail.isReductionValid(), "等于原保额属边界值，不得判为不成立");
    }

    @Test
    @DisplayName("反例：新保额超过原保额 → 减额不成立（不构成「减额」）")
    void shouldRejectWhenReducedSumInsuredExceedsOriginal() {
        ReducedPaidUpDetail detail = new ReducedPaidUpDetail(
                new BigDecimal("90000"), new BigDecimal("100000"), new BigDecimal("0.5"));

        // 90000 / 0.5 = 180000.00 > 100000
        assertEquals(new BigDecimal("180000.00"), detail.reducedSumInsured());
        assertFalse(detail.isReductionValid(), "新保额大于原保额时必须判为不成立");
    }

    @Test
    @DisplayName("正例：净费率是「每元保额的保费」，可大于 1，不设上界")
    void shouldAllowRateGreaterThanOne() {
        ReducedPaidUpDetail detail = new ReducedPaidUpDetail(
                new BigDecimal("200000"), new BigDecimal("100000"), new BigDecimal("1.5"));

        // 200000 / 1.5 = 133333.333... → 133333.33；此时已超原保额，由减额校验拒绝而非构造器
        assertEquals(new BigDecimal("133333.33"), detail.reducedSumInsured());
        assertFalse(detail.isReductionValid());
    }

    @Test
    @DisplayName("构造器不变量：现金价值/原保额/净费率任一非正 → 构造即拒绝")
    void shouldRejectNonPositiveInputs() {
        assertThrows(IllegalArgumentException.class, () -> new ReducedPaidUpDetail(
                BigDecimal.ZERO, new BigDecimal("100000"), new BigDecimal("0.5")));
        assertThrows(IllegalArgumentException.class, () -> new ReducedPaidUpDetail(
                new BigDecimal("10000"), new BigDecimal("-1"), new BigDecimal("0.5")));
        assertThrows(IllegalArgumentException.class, () -> new ReducedPaidUpDetail(
                new BigDecimal("10000"), new BigDecimal("100000"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ReducedPaidUpDetail(
                null, new BigDecimal("100000"), new BigDecimal("0.5")));
    }
}
