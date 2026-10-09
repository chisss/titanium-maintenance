package com.titanium.maintenance.port.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保单财务取数端口（出口/driven port，g04-02）。
 * <p>
 * 保全域退费/贷款/减额缴清等现金价值类动作的取数底座：表达对保单域「财务快照」的能力需求，
 * 由基础设施层 {@code PolicyFinancialAdapter} 经 Feign 调保单域
 * {@code GET /api/v1/policies/{policyId}/financial-snapshot} 实现，领域侧不依赖任何远程响应类型
 * （防腐）。与 {@link PolicyServicePort}（保单存在性/状态快照）职责分立，互不混入。
 * </p>
 * <p>
 * 🔴 失败关闭契约：保单域应答业务码失败（保单不存在/财务要素缺失）或应答体无效时，
 * Adapter 抛 {@code PolicyFinancialUnavailableException}（{@code MAINTENANCE_POLICY_FINANCIAL_UNAVAILABLE}）；
 * 保单域不可达时抛 {@code MaintenanceRemoteCallException}（HTTP 502 语义）。调用方不得以 0 兜底。
 * </p>
 */
public interface PolicyFinancialPort {

    /**
     * 取保单财务快照（强一致，由保单域组装）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 财务快照；缺数据一律抛领域异常（失败关闭），绝不返回 null
     */
    PolicyFinancialData getFinancialSnapshot(String policyId, String tenantId);

    /**
     * 保单财务快照（领域侧类型化视图，屏蔽远程响应细节）。
     *
     * @param policyId 保单ID
     * @param cashValue 现金价值金额（产品域退保价值策略计算）
     * @param sumInsured 主险段基本保额（减额缴清核定「新保额 ≤ 原保额」的原保额基准）
     * @param mainSegmentId 主险段ID（{@code policyProductId}）：减额缴清回写
     *                      {@code policy.coverage.sumInsured} 时该字段的 {@code objectId}，与
     *                      {@code sumInsured} 同为「主险段」的两个分量，由保单域同批下发
     * @param loanOutstandingPrincipal 贷款未偿本金（无未结贷款为 0）
     * @param loanOutstandingInterest 贷款未偿利息（无未结贷款为 0）
     * @param arrearsAmount 欠费金额（无欠费为 0）
     * @param dividendBalance 红利账户余额
     * @param accountValue 投资账户价值
     * @param effectiveDate 保单生效日
     * @param paidInstallments 已缴期数
     * @param issuanceBizNo 出单业务流水号（退保退费登记的 {@code originalBizNo}；<b>可空</b>，
     *                      存量保单无此值）
     * @param annualPremium 年缴保费（未满期保费按日折算的基数；<b>可空</b>）
     * @param coverageEndDate 保障止期（未满期保费剩余天数的终点；<b>可空</b>，终身型保单无终止日）
     * @param premiumPaidUp 缴费是否期满（趸缴视为期满）
     * <p>
     * 🔴 <b>退费口径四项可空，不参与失败关闭</b>：它们只服务退保退费的未满期保费基准，贷款与
     * 减额缴清路径不读，故保单域不因缺失而拒绝出快照。拒绝责任由使用点承担——退保在未满期保费
     * 基准下遇年保费/止期缺失须<b>拒算</b>，不得按 0 退。
     * </p>
     */
    record PolicyFinancialData(
            String policyId,
            BigDecimal cashValue,
            BigDecimal sumInsured,
            String mainSegmentId,
            BigDecimal loanOutstandingPrincipal,
            BigDecimal loanOutstandingInterest,
            BigDecimal arrearsAmount,
            BigDecimal dividendBalance,
            BigDecimal accountValue,
            LocalDate effectiveDate,
            Integer paidInstallments,
            String issuanceBizNo,
            BigDecimal annualPremium,
            LocalDate coverageEndDate,
            boolean premiumPaidUp) {

        /**
         * 兼容未消费退费口径的调用方（贷款放款/减额缴清核定）。
         * <p>
         * 这两条链路与缴费条件无关，不读退费口径四项，故补四者缺省值即可，无须逐处改构造点。
         * </p>
         */
        public PolicyFinancialData(
                String policyId,
                BigDecimal cashValue,
                BigDecimal sumInsured,
                String mainSegmentId,
                BigDecimal loanOutstandingPrincipal,
                BigDecimal loanOutstandingInterest,
                BigDecimal arrearsAmount,
                BigDecimal dividendBalance,
                BigDecimal accountValue,
                LocalDate effectiveDate,
                Integer paidInstallments) {
            this(policyId, cashValue, sumInsured, mainSegmentId, loanOutstandingPrincipal,
                    loanOutstandingInterest, arrearsAmount, dividendBalance, accountValue, effectiveDate,
                    paidInstallments, null, null, null, false);
        }
    }
}
