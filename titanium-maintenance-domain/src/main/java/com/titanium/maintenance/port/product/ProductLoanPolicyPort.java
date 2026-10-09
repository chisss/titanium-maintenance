package com.titanium.maintenance.port.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保单贷款策略查询端口（出口/driven port，g04-03）。
 * <p>
 * 保全域贷款案件生效出口的策略取数底座：表达对产品域「已发布贷款策略（可贷比例 + 年利率）」的能力需求，
 * 由基础设施层 {@code ProductLoanPolicyAdapter} 经 Feign 调产品域
 * {@code GET /api/v1/loan-policies/effective} 实现，领域侧不依赖任何远程响应类型（防腐）。
 * 取到的比例与利率<b>冻结</b>进保单域贷款账户后即可弃用（放款时点快照语义）。
 * </p>
 * <p>
 * 🔴 失败关闭契约：产品域应答业务码失败（该时点无已发布策略）或应答体无效时，Adapter 抛
 * {@code ProductLoanPolicyUnavailableException}；产品域不可达时抛 {@code MaintenanceRemoteCallException}
 * （HTTP 502 语义）。调用方不得以默认比例/利率兜底。
 * </p>
 */
public interface ProductLoanPolicyPort {

    /**
     * 按业务时点查询产品当前已发布的保单贷款策略（纯查表，零副作用）。
     *
     * @param productId 产品ID
     * @param businessTime 业务时点（定位适用策略版本；可为空，空则取当前时间）
     * @return 贷款策略；无适用策略一律抛领域异常（失败关闭），绝不返回 null
     */
    LoanPolicy queryEffectiveLoanPolicy(String productId, LocalDateTime businessTime);

    /**
     * 已发布贷款策略（防腐值对象）：可贷比例与年利率为放款冻结要素。
     *
     * @param policyId 贷款策略ID
     * @param productId 产品ID
     * @param loanableRatio 可贷比例（0-1，如 0.8 表示最高可贷现金价值的 80%）
     * @param interestRate 贷款年利率（0-1，如 0.05 表示 5%）
     * @param policyContentHash 策略内容哈希（取证用，可据其复核所用策略未被篡改）
     */
    record LoanPolicy(
            String policyId,
            String productId,
            BigDecimal loanableRatio,
            BigDecimal interestRate,
            String policyContentHash) {
    }
}
