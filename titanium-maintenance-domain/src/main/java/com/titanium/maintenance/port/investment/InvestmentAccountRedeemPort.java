package com.titanium.maintenance.port.investment;

import java.math.BigDecimal;

/**
 * 投资账户部分领取赎回出口端口（六边形架构 driven port）
 * <p>
 * 保全生效环节中「部分领取（PARTIAL_WITHDRAWAL）」类案件的执行出口：按<b>关联保单</b>定位投资账户，把客户
 * 实领金额折算为赎回单位数并从账户中赎回。账户为何按保单而非账户ID寻址——保全案件只持有保单标识，账户是
 * 保单在投资域的下游派生（一保单可有多个形态账户），故由适配器完成「保单 → 账户」的解析；解析结果不唯一时
 * 明确失败而非静默择一（与 {@link InvestmentAccountSwitchPort} 同规）。
 * </p>
 * <p>
 * 🔴 <b>为何递金额而不递单位数</b>：单位数由账户按<b>自身当日净值</b>折算（见对端契约
 * {@code InvestmentAccountApi.RedeemByAmountRequest}）。若本域先自行折算再走「按单位赎回」，会把「查净值」
 * 与「卖出」拆成两步，二者之间的净值变动会让客户实领金额与请求金额<b>静默不等</b>——故折算入口只存在于
 * 账户侧（精度同 AC-03：单位 4 位 HALF_UP），本域只负责把「领多少钱」这一事实递过去。
 * </p>
 * <p>
 * 🔴 <b>与账户转换的语义分野</b>：账户转换只变换投资标的与持有单位（<b>价值守恒</b>，不动保单状态与字段）；
 * 部分领取则<b>减少账户价值</b>，是资金动作，其对应的合同侧效果由 policy 出口的那笔收付差额承载
 * （同一案件的 {@code ApplicationRequest.amountEffect}）。二者是同一批生效任务的两种互斥路径，不得混用。
 * </p>
 * <p>
 * 🔴 <b>空值语义</b>：保单名下无可用投资账户时返回 {@code null}（对端未命中，原样返回，由调用方决定处置），
 * <b>不得</b>以抛异常表达「不存在」；而远程服务不可达、契约反序列化失败、对端业务拒绝一律 fail-closed 抛
 * {@code MaintenanceRemoteCallException}，禁止把服务故障伪装成「账户不存在」。
 * </p>
 */
public interface InvestmentAccountRedeemPort {

    /**
     * 按关联保单执行部分领取赎回
     *
     * @param request 赎回请求（保单、案件、实领金额与币种）
     * @return 赎回后的账户权威事实；保单名下无可用账户时返回 {@code null}
     */
    InvestmentRedeemFact redeemByPolicy(InvestmentRedeemRequest request);

    /**
     * 部分领取赎回请求（本域自有入参，不暴露对端契约类型）
     *
     * @param tenantId     租户ID
     * @param policyId     关联保单ID（账户的寻址入口）
     * @param maintenanceId 保全案件号（用于赎回原因留痕，使重复赎回可事后按案检出）
     * @param redeemAmount 客户实领金额（须大于零；单位数由账户按当日净值折算）
     * @param currency     金额币种（为空时由对端按账户币种处理）
     * @param operatorId   操作人
     */
    record InvestmentRedeemRequest(
            String tenantId,
            String policyId,
            String maintenanceId,
            BigDecimal redeemAmount,
            String currency,
            String operatorId) {

        /**
         * 赎回原因前缀（跨域业务描述按红线 20 常量化；后缀为案件号）。
         * <p>
         * 原因<b>由案件号派生、不可由调用方自报</b>：投资域事件流据此可按案检索本次赎回，使「赎回成功但案件
         * 回执失败」这类重试窗口产生的重复赎回可被事后检出（本端口未引入幂等键，见调用方 javadoc 的窗口登记）。
         * </p>
         */
        private static final String REASON_PREFIX = "MAINTENANCE_PARTIAL_WITHDRAWAL:";

        /** 跨域递交的赎回原因：稳定且唯一对应一个保全案件。 */
        public String reason() {
            return REASON_PREFIX + maintenanceId;
        }
    }

    /**
     * 部分领取赎回的权威事实（字段全部取自对端应答，本域不本地重算赎回明细）
     *
     * @param accountId    投资账户ID
     * @param unitPrice    赎回后单位净值（即本次折算所用的当日净值）
     * @param totalUnits   赎回后持有单位数
     * @param accountValue 赎回后账户价值
     * @param currency     账户币种
     */
    record InvestmentRedeemFact(
            String accountId,
            BigDecimal unitPrice,
            BigDecimal totalUnits,
            BigDecimal accountValue,
            String currency) {
    }
}
