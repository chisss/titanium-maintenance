package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 退保价值策略快照值对象（Product 退保政策证据的聚合内聚载体）
 * <p>
 * 收敛聚合中 10 个平铺的退保策略字段（策略编码/版本/内容哈希/保单年度/犹豫期天数/退费类型/
 * 犹豫期标志/现金价值率/客户留存金额/内扣成本率）为单一值对象，承载「同一退保案件不得绑定
 * 不同策略事实」的幂等比较（{@link #sameAs(SurrenderPolicySnapshot)}）。
 * </p>
 * <p>
 * 🔴 <b>本值对象刻意不提供 {@code isWithinCoolingOff()} 派生方法</b>：{@code withinCoolingOff} 是本 record
 * 的<b>分量</b>，按 JavaBean 规则再加 {@code is-} 前缀方法会让 Jackson 在属性 "withinCoolingOff" 上
 * 看到两个访问器（record 分量 accessor + is-getter）——序列化侧要么报访问器冲突，要么（若用
 * {@code @JsonIgnore} 屏蔽派生 getter）把<b>同名分量一并屏蔽</b>，导致快照静默丢字段。同族教训见
 * {@code SurrenderDetail#isWithinCoolingOff}（D-501-19：派生 getter 被序列化器写进事件载荷导致重放
 * UnrecognizedPropertyException），二者的处置方向相反、成因同源：非分量派生 getter 必须屏蔽，
 * 同名分量则不得再加派生 getter。
 * </p>
 * <p>
 * 🔴 本值对象进聚合状态、随<b>快照</b>序列化（事件类不受影响，字段保持平铺，见 m25-02 设计：事件零改动）。
 * </p>
 *
 * @param policyCode 退保政策编码
 * @param policyVersion 退保政策版本
 * @param policyContentHash 退保政策内容哈希（64 位；有效性由命令侧校验，本值对象不重复校验）
 * @param policyYear 保单年度
 * @param coolingOffDays 犹豫期天数
 * @param refundType 退费类型（Product 侧口径字符串）
 * @param withinCoolingOff 是否处于犹豫期内（已消可空 Boolean：未标记按 false 归一，见 {@link #of}）
 * @param cashValueRate 现金价值率
 * @param retainedCustomerAmount 客户留存金额
 * @param internalCostRetentionRate 内扣成本率
 */
public record SurrenderPolicySnapshot(String policyCode, String policyVersion, String policyContentHash,
        Integer policyYear, Integer coolingOffDays, String refundType, boolean withinCoolingOff,
        BigDecimal cashValueRate, BigDecimal retainedCustomerAmount, BigDecimal internalCostRetentionRate) {

    /** 空快照（未记录退保策略证据）；有效性以 {@link #policyContentHash()} 非空为准 */
    private static final SurrenderPolicySnapshot EMPTY = new SurrenderPolicySnapshot(null, null, null, null, null,
            null, false, null, null, null);

    /**
     * 空快照工厂：聚合在未经历退保价值记录事件时按本值读取，调用方无需对聚合字段判空。
     */
    public static SurrenderPolicySnapshot empty() {
        return EMPTY;
    }

    /**
     * 从事件/命令载荷构造；{@code withinCoolingOff} 可空（事件载荷为 {@code Boolean}，历史/异常载荷可能为 null），
     * 按「未标记即非犹豫期」归一为 primitive boolean——这是本值对象**消可空 Boolean** 的落点。
     */
    public static SurrenderPolicySnapshot of(String policyCode, String policyVersion, String policyContentHash,
            Integer policyYear, Integer coolingOffDays, String refundType, Boolean withinCoolingOff,
            BigDecimal cashValueRate, BigDecimal retainedCustomerAmount, BigDecimal internalCostRetentionRate) {
        return new SurrenderPolicySnapshot(policyCode, policyVersion, policyContentHash, policyYear, coolingOffDays,
                refundType, Boolean.TRUE.equals(withinCoolingOff), cashValueRate, retainedCustomerAmount,
                internalCostRetentionRate);
    }

    /**
     * 逐项等价比较（同案幂等判据）。
     * <p>
     * 金额分量用 {@code compareTo}（scale 不敏感，与原聚合逐字段比较语义一致）；空值安全——
     * 双方同空视为等价、单侧为空视为不等价（原实现对命令侧 null 金额会直接 NPE，此处收敛为确定判定，
     * 不改变任何原可完成比较的路径的结果）。
     * </p>
     */
    public boolean sameAs(SurrenderPolicySnapshot other) {
        return other != null
                && Objects.equals(policyCode, other.policyCode)
                && Objects.equals(policyVersion, other.policyVersion)
                && Objects.equals(policyContentHash, other.policyContentHash)
                && Objects.equals(policyYear, other.policyYear)
                && Objects.equals(coolingOffDays, other.coolingOffDays)
                && Objects.equals(refundType, other.refundType)
                && withinCoolingOff == other.withinCoolingOff
                && sameDecimal(cashValueRate, other.cashValueRate)
                && sameDecimal(retainedCustomerAmount, other.retainedCustomerAmount)
                && sameDecimal(internalCostRetentionRate, other.internalCostRetentionRate);
    }

    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }
}
