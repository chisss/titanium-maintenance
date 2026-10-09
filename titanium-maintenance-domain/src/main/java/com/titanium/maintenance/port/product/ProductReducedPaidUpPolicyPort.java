package com.titanium.maintenance.port.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 减额缴清净费率策略查询端口（出口/driven port，g04-05）。
 * <p>
 * 保全域减额缴清案件生效出口的策略取数底座：表达对产品域「已发布减额缴清净费率策略」的能力需求，
 * 由基础设施层 {@code ProductReducedPaidUpPolicyAdapter} 经 Feign 调产品域
 * {@code GET /api/v1/reduced-paid-up-policies/effective} 实现，领域侧不依赖任何远程响应类型（防腐）。
 * </p>
 * <p>
 * 费率语义：「每元保额趸缴净费率」，用于核定新保额
 * （{@code 新保额 = 现金价值 ÷ 每元保额趸缴净费率}）。🔴 <b>它不是比例</b>——高龄/高保单年度下
 * 该费率可大于 1，故实现与消费方均不得对其设「≤ 1」上界。
 * </p>
 * <p>
 * 🔴 失败关闭契约：产品域应答业务码失败（该时点无已发布策略）或应答体无效时，Adapter 抛
 * {@code ProductReducedPaidUpPolicyUnavailableException}；产品域不可达时抛
 * {@code MaintenanceRemoteCallException}（HTTP 502 语义）。调用方不得以近似费率/默认费率兜底。
 * </p>
 */
public interface ProductReducedPaidUpPolicyPort {

    /**
     * 按业务时点查询产品当前已发布的减额缴清净费率策略（纯查表，零副作用）。
     *
     * @param productId 产品ID
     * @param businessTime 业务时点（定位适用策略版本；可为空，空则取当前时间）
     * @return 费率策略；无适用策略一律抛领域异常（失败关闭），绝不返回 null
     */
    ReducedPaidUpPolicy queryEffectiveReducedPaidUpPolicy(String productId, LocalDateTime businessTime);

    /**
     * 已发布减额缴清净费率策略（防腐值对象）：费率为核定新保额的冻结要素。
     *
     * @param policyId 减额缴清策略ID
     * @param productId 产品ID
     * @param netSinglePremiumRatePerUnit 每元保额趸缴净费率（必须为正；无上界——高龄保单可大于 1）
     * @param policyContentHash 策略内容哈希（取证用，可据其复核所用策略未被篡改）
     */
    record ReducedPaidUpPolicy(
            String policyId,
            String productId,
            BigDecimal netSinglePremiumRatePerUnit,
            String policyContentHash) {
    }
}
