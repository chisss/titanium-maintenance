package com.titanium.maintenance.valueobject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 退保价值策略快照值对象测试（m25-02 DSN-01b）
 * <p>
 * 覆盖三件事：① {@link SurrenderPolicySnapshot#empty()} 的空值语义；② {@link SurrenderPolicySnapshot#of} 对
 * 可空 {@code Boolean withinCoolingOff} 的归一（本值对象消可空 Boolean 的落点）；③
 * {@link SurrenderPolicySnapshot#sameAs} 的逐项等价判据（含 BigDecimal scale 不敏感与 null 安全）。
 * 值对象可 {@code new} 直测，不启动容器。
 * </p>
 */
class SurrenderPolicySnapshotTest {

    @Test
    @DisplayName("空快照：全部分量为空，withinCoolingOff 归一为 false（primitive 不可为 null）")
    void shouldExposeEmptySnapshotWithAllNullComponents() {
        SurrenderPolicySnapshot empty = SurrenderPolicySnapshot.empty();

        assertNull(empty.policyCode());
        assertNull(empty.policyVersion());
        assertNull(empty.policyContentHash());
        assertNull(empty.policyYear());
        assertNull(empty.coolingOffDays());
        assertNull(empty.refundType());
        assertFalse(empty.withinCoolingOff(), "empty 按「未标记即非犹豫期」归一");
        assertNull(empty.cashValueRate());
        assertNull(empty.retainedCustomerAmount());
        assertNull(empty.internalCostRetentionRate());
    }

    @Test
    @DisplayName("of 归一：withinCoolingOff 传 null → false；传 TRUE → true")
    void shouldNormalizeNullableBooleanWithinCoolingOff() {
        assertFalse(ofWithWithinCoolingOff(null).withinCoolingOff(), "null 按「未标记即非犹豫期」归一为 false");
        assertFalse(ofWithWithinCoolingOff(Boolean.FALSE).withinCoolingOff());
        assertTrue(ofWithWithinCoolingOff(Boolean.TRUE).withinCoolingOff());
    }

    @Test
    @DisplayName("sameAs 正例：逐项一致（BigDecimal 同值不同 scale）判等价")
    void shouldTreatSameContentWithDifferentDecimalScaleAsEqual() {
        SurrenderPolicySnapshot snapshot = snapshot(new BigDecimal("0.75"), new BigDecimal("1000.00"),
                new BigDecimal("0.05"));
        SurrenderPolicySnapshot replayed = snapshot(new BigDecimal("0.7500"), new BigDecimal("1000.0"),
                new BigDecimal("0.050"));

        assertTrue(snapshot.sameAs(replayed), "BigDecimal 比较用 compareTo，scale 不影响等价（与原聚合逐字段比较语义一致）");
    }

    @Test
    @DisplayName("sameAs 反例：任一业务分量不同即不等价（含 withinCoolingOff 标志）")
    void shouldTreatAnyComponentDifferenceAsNotEqual() {
        SurrenderPolicySnapshot base = snapshot(new BigDecimal("0.75"), new BigDecimal("1000.00"),
                new BigDecimal("0.05"));

        assertFalse(base.sameAs(new SurrenderPolicySnapshot("policy-2", base.policyVersion(),
                base.policyContentHash(), base.policyYear(), base.coolingOffDays(), base.refundType(),
                base.withinCoolingOff(), base.cashValueRate(), base.retainedCustomerAmount(),
                base.internalCostRetentionRate())));
        assertFalse(base.sameAs(new SurrenderPolicySnapshot(base.policyCode(), "v2", base.policyContentHash(),
                base.policyYear(), base.coolingOffDays(), base.refundType(), base.withinCoolingOff(),
                base.cashValueRate(), base.retainedCustomerAmount(), base.internalCostRetentionRate())));
        assertFalse(base.sameAs(new SurrenderPolicySnapshot(base.policyCode(), base.policyVersion(), "hash-2",
                base.policyYear(), base.coolingOffDays(), base.refundType(), base.withinCoolingOff(),
                base.cashValueRate(), base.retainedCustomerAmount(), base.internalCostRetentionRate())));
        assertFalse(base.sameAs(new SurrenderPolicySnapshot(base.policyCode(), base.policyVersion(),
                base.policyContentHash(), 2, base.coolingOffDays(), base.refundType(), base.withinCoolingOff(),
                base.cashValueRate(), base.retainedCustomerAmount(), base.internalCostRetentionRate())));
        assertFalse(base.sameAs(new SurrenderPolicySnapshot(base.policyCode(), base.policyVersion(),
                base.policyContentHash(), base.policyYear(), 30, base.refundType(), base.withinCoolingOff(),
                base.cashValueRate(), base.retainedCustomerAmount(), base.internalCostRetentionRate())));
        assertFalse(base.sameAs(new SurrenderPolicySnapshot(base.policyCode(), base.policyVersion(),
                base.policyContentHash(), base.policyYear(), base.coolingOffDays(), "OTHER",
                base.withinCoolingOff(), base.cashValueRate(), base.retainedCustomerAmount(),
                base.internalCostRetentionRate())));
        assertFalse(base.sameAs(new SurrenderPolicySnapshot(base.policyCode(), base.policyVersion(),
                base.policyContentHash(), base.policyYear(), base.coolingOffDays(), base.refundType(), false,
                base.cashValueRate(), base.retainedCustomerAmount(), base.internalCostRetentionRate())),
                "withinCoolingOff 参与等价判据（true → false 即不等价）");
        assertFalse(base.sameAs(new SurrenderPolicySnapshot(base.policyCode(), base.policyVersion(),
                base.policyContentHash(), base.policyYear(), base.coolingOffDays(), base.refundType(),
                base.withinCoolingOff(), new BigDecimal("0.80"), base.retainedCustomerAmount(),
                base.internalCostRetentionRate())));
        assertFalse(base.sameAs(new SurrenderPolicySnapshot(base.policyCode(), base.policyVersion(),
                base.policyContentHash(), base.policyYear(), base.coolingOffDays(), base.refundType(),
                base.withinCoolingOff(), base.cashValueRate(), new BigDecimal("999.00"),
                base.internalCostRetentionRate())));
        assertFalse(base.sameAs(new SurrenderPolicySnapshot(base.policyCode(), base.policyVersion(),
                base.policyContentHash(), base.policyYear(), base.coolingOffDays(), base.refundType(),
                base.withinCoolingOff(), base.cashValueRate(), base.retainedCustomerAmount(),
                new BigDecimal("0.06"))));
    }

    @Test
    @DisplayName("sameAs 边界：空快照与空快照等价、与有值快照不等价、与 null 不等价")
    void shouldBeNullSafeAndEmptyTolerant() {
        SurrenderPolicySnapshot empty = SurrenderPolicySnapshot.empty();

        assertTrue(empty.sameAs(SurrenderPolicySnapshot.empty()), "双侧同空视为等价（未记录 = 未记录）");
        assertFalse(empty.sameAs(snapshot(new BigDecimal("0.75"), new BigDecimal("1000.00"),
                new BigDecimal("0.05"))), "单侧为空视为不等价");
        assertFalse(empty.sameAs(null), "other 为 null 时安全返回 false，不抛 NPE");
    }

    private static SurrenderPolicySnapshot ofWithWithinCoolingOff(Boolean withinCoolingOff) {
        return SurrenderPolicySnapshot.of("policy-1", "v1", "hash-1", 1, 15, "CASH_VALUE", withinCoolingOff,
                new BigDecimal("0.75"), new BigDecimal("1000.00"), new BigDecimal("0.05"));
    }

    private static SurrenderPolicySnapshot snapshot(BigDecimal cashValueRate, BigDecimal retainedCustomerAmount,
            BigDecimal internalCostRetentionRate) {
        return SurrenderPolicySnapshot.of("policy-1", "v1", "hash-1", 1, 15, "CASH_VALUE", Boolean.TRUE,
                cashValueRate, retainedCustomerAmount, internalCostRetentionRate);
    }
}
