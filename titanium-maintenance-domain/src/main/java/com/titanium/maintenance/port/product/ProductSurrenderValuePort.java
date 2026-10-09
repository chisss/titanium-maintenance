package com.titanium.maintenance.port.product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;

/** Product 退保价值确认端口。 */
public interface ProductSurrenderValuePort {

    SurrenderFact calculate(SurrenderRequest request);

    /**
     * 退保价值试算请求。
     * <p>
     * 🔴 <b>{@code annualPremium}/{@code coverageEndDate}/{@code premiumPaidUp} 是未满期保费口径的输入事实</b>
     * （g05-02），一律由<b>保单域</b>经 g04-02 取数契约下发，本域只做搬运、不做任何口径推导——
     * 「年保费 = 期保费 × 每年期数」这类换算表只在保单域 {@code LinePaymentTerms} 里有一份，在本域
     * 再算一遍就是第二套保费口径。
     * </p>
     * <p>
     * 🔴 <b>这三项刻意不给兼容构造器</b>：{@code null}/{@code null}/{@code false} 会被对端读成
     * 「事实不可得」，在未满期保费基准下直接失败关闭（{@code SURRENDER_PREMIUM_FACTS_MISSING}）。
     * 留一个把它们默认为空的便捷构造器，等于给调用方一条「编译通过、运行期才炸」的暗道。
     * 缴费期满（含趸缴）时传 {@code premiumPaidUp=true}、年保费与止期可空——「期满」本身即该项为 0
     * 的充分理由。
     * </p>
     * <p>
     * 🔴 <b>{@code loanOutstandingPrincipal}/{@code loanOutstandingInterest} 是「退费先行抵扣」的输入事实</b>
     * （g05-03）：保单贷款未偿本金与未偿利息，由保单域按 g04-03 同一贷款口径（已过账余额）聚合下发，
     * 本域只搬运、不重算。保单域响应契约（{@code PolicyFinancialResponse}）已强制两项非空——
     * <b>无未结贷款是 0，不是 null</b>，故正常路径下不存在 {@code null}。本域<b>不为它们另设失败关闭</b>：
     * 能否抵扣由产品域在<b>使用点</b>判定，缺任一即以 {@code SURRENDER_LOAN_FACTS_MISSING} 拒算，
     * 本域不预判、更不以 0 兜底。
     * </p>
     * <p>
     * 🔴 <b>{@code overduePremium}/{@code dividendBalance}/{@code accountValue} 是「账户余额」的输入事实</b>
     * （g05-04）：宽限期内欠缴保费（先行抵扣）与红利账户余额、投资账户价值（一并退还），同样由保单域
     * 财务快照下发，本域只搬运。三项在保单域响应契约里均强制非空——<b>无欠费/无红利/无投资账户是 0，
     * 不是 null</b>。🔴 <b>三项必须原样过桥到产品域再合并</b>：「红利 + 投资账户价值 = 红利及账户价值」
     * 是一个退费因子的合并口径，属产品域（退费口径权威域）的决策，本域与适配器<b>不得先行求和</b>，
     * 否则口径就落到了错误的一层，且原始分量永远丢失。
     * </p>
     *
     * @param annualPremium            年缴保费（未满期保费按日折算的基数；不可得时为 {@code null}）
     * @param coverageEndDate          保障止期（剩余保障天数的计算终点；终身型为 {@code null}）
     * @param premiumPaidUp            缴费是否期满（含趸缴）：期满即无未满期保费，该项恒为 0
     * @param loanOutstandingPrincipal 贷款未偿本金（无未结贷款为 0，由保单域下发）
     * @param loanOutstandingInterest  贷款未偿利息（无未结贷款为 0，由保单域下发）
     * @param overduePremium           宽限期内欠缴保费（无欠费为 0，由保单域下发）
     * @param dividendBalance          红利账户余额（未参与分红为 0，由保单域下发）
     * @param accountValue             投资账户价值（未挂接投资账户为 0，由保单域下发）
     */
    record SurrenderRequest(
            String tenantId,
            String surrenderRequestId,
            String bizNo,
            String originalBizNo,
            String originalCalculationId,
            LocalDate policyEffectiveDate,
            LocalDate surrenderDate,
            Integer policyYear,
            LocalDateTime businessTime,
            String reason,
            BigDecimal annualPremium,
            LocalDate coverageEndDate,
            boolean premiumPaidUp,
            BigDecimal loanOutstandingPrincipal,
            BigDecimal loanOutstandingInterest,
            BigDecimal overduePremium,
            BigDecimal dividendBalance,
            BigDecimal accountValue) {
    }

    record SurrenderFact(
            String surrenderRequestId,
            String policyCode,
            String policyVersion,
            String policyContentHash,
            Integer policyYear,
            Integer coolingOffDays,
            String refundType,
            Boolean withinCoolingOff,
            BigDecimal cashValueRate,
            BigDecimal refundAmount,
            BigDecimal retainedCustomerAmount,
            BigDecimal internalCostRetentionRate,
            String originalCalculationId,
            String originalResultHash,
            String replacementCalculationId,
            String replacementResultHash,
            String adjustmentId,
            String requestHash,
            String adjustmentResultHash,
            String pricingPlanVersion,
            String pricingPlanContentHash,
            MaintenanceBalanceDirection direction,
            BigDecimal amount,
            String currency,
            List<SurrenderFactorFact> factors) {

        /**
         * 兼容旧测试与历史适配器；新契约由 Product 返回真实原/替代结果摘要（无因子明细）。
         */
        public SurrenderFact(
                String surrenderRequestId,
                String policyCode,
                String policyVersion,
                String policyContentHash,
                Integer policyYear,
                Integer coolingOffDays,
                String refundType,
                Boolean withinCoolingOff,
                BigDecimal cashValueRate,
                BigDecimal refundAmount,
                BigDecimal retainedCustomerAmount,
                BigDecimal internalCostRetentionRate,
                String originalCalculationId,
                String originalResultHash,
                String replacementCalculationId,
                String replacementResultHash,
                String adjustmentId,
                String requestHash,
                String adjustmentResultHash,
                String pricingPlanVersion,
                String pricingPlanContentHash,
                MaintenanceBalanceDirection direction,
                BigDecimal amount,
                String currency) {
            this(surrenderRequestId, policyCode, policyVersion, policyContentHash, policyYear, coolingOffDays,
                    refundType, withinCoolingOff, cashValueRate, refundAmount, retainedCustomerAmount,
                    internalCostRetentionRate, originalCalculationId, originalResultHash, replacementCalculationId,
                    replacementResultHash, adjustmentId, requestHash, adjustmentResultHash, pricingPlanVersion,
                    pricingPlanContentHash, direction, amount, currency, List.of());
        }

        /** 兼容旧测试与历史适配器；新契约由 Product 返回真实原/替代结果摘要。 */
        public SurrenderFact(
                String surrenderRequestId,
                String policyCode,
                String policyVersion,
                String policyContentHash,
                Integer policyYear,
                Integer coolingOffDays,
                String refundType,
                Boolean withinCoolingOff,
                BigDecimal cashValueRate,
                BigDecimal refundAmount,
                BigDecimal retainedCustomerAmount,
                BigDecimal internalCostRetentionRate,
                String originalCalculationId,
                String replacementCalculationId,
                String adjustmentId,
                String adjustmentResultHash,
                MaintenanceBalanceDirection direction,
                BigDecimal amount,
                String currency) {
            this(surrenderRequestId, policyCode, policyVersion, policyContentHash, policyYear,
                    coolingOffDays, refundType, withinCoolingOff, cashValueRate, refundAmount,
                    retainedCustomerAmount, internalCostRetentionRate, originalCalculationId,
                    adjustmentResultHash, replacementCalculationId, adjustmentResultHash,
                    adjustmentId, adjustmentResultHash, adjustmentResultHash,
                    policyVersion, policyContentHash, direction, amount, currency, List.of());
        }
    }

    /**
     * 退费因子（对端 Product 退费明细的**防腐镜像**）。
     * <p>
     * 🔴 字段一律用字符串码承接，**不依赖对端枚举**：对端枚举常量增减不应导致本域编译失败，
     * 未知码在此原样透传、由展示与对账方按同一 code 解释（跨域防腐，项目 CLAUDE.md §3.4.11 ③）。
     * 本记录承载展示与对账所需的最小口径，退费金额的**权威仍在 Product 域**，本域不得据此重算。
     * </p>
     *
     * @param factorType 因子类型码
     * @param direction  金额方向码（CREDIT / DEBIT / NONE）
     * @param amount     金额绝对值
     * @param explainKey 文案键
     */
    record SurrenderFactorFact(
            String factorType,
            String direction,
            BigDecimal amount,
            String explainKey) {
    }
}
