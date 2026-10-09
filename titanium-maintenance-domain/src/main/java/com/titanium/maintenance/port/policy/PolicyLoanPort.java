package com.titanium.maintenance.port.policy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保单贷款端口（出口/driven port，g04-03 放款；g04-04 还款）。
 * <p>
 * 保全域贷款案件生效出口的写入底座：表达对保单域「贷款账户放款 / 还款」的能力需求，由基础设施层
 * {@code PolicyLoanAdapter} 经 Feign 调保单域 {@code POST /api/v1/loans/disburse|repay} 实现，领域侧不依赖
 * 任何远程响应类型（防腐）。放款与还款事实的权威在保单域贷款账户聚合，本域只发起并接收回执。
 * </p>
 * <p>
 * 🔴 失败关闭契约：保单域应答业务码失败（保单不存在/未生效/金额利率非法/重复放款/还款超额等）或应答体无效时，
 * Adapter 抛 {@code PolicyLoanUnavailableException}；保单域不可达时抛 {@code MaintenanceRemoteCallException}
 * （HTTP 502 语义）。调用方不得以 0 或空回执兜底。
 * </p>
 */
public interface PolicyLoanPort {

    /**
     * 发起保单贷款放款（同步等保单域入账返回）。
     *
     * @param order 放款指令（贷款业务号由本域生成，全局唯一幂等键）
     * @return 保单域放款回执；失败一律抛领域异常（失败关闭），绝不返回 null
     */
    PolicyLoanDisbursementReceipt disburse(PolicyLoanDisbursementOrder order);

    /**
     * 发起保单贷款还款（同步等保单域记账返回）。
     * <p>
     * 🔴 <b>分配不复算</b>：本次还款在「未偿本金 + 已计未付利息」之间的拆分由保单域贷款账户聚合按持有
     * 的全量还款记录现场计算（先息后本、单利 365 分段计息），调用方只传还款总额，从回执读取分配结果。
     * </p>
     *
     * @param order 还款指令（还款业务号由本域生成，全局唯一幂等键）
     * @return 保单域还款回执（含权威分配与还款后余额）；失败一律抛领域异常（失败关闭），绝不返回 null
     */
    PolicyLoanRepaymentReceipt repay(PolicyLoanRepaymentOrder order);

    /**
     * 放款指令（保全域 → 保单域）：放款要素冻结进保单域贷款账户。
     *
     * @param loanId 贷款明细业务号（本域生成，全局唯一幂等键）
     * @param policyId 保单ID
     * @param loanAmount 放款本金
     * @param interestRate 冻结利率（放款时点取产品域已发布贷款策略，[0,1]）
     * @param loanDate 放款日期
     */
    record PolicyLoanDisbursementOrder(
            String loanId,
            String policyId,
            BigDecimal loanAmount,
            BigDecimal interestRate,
            LocalDateTime loanDate) {
    }

    /**
     * 放款回执（保单域 → 保全域）：与指令同要素回显，供入账、出账与留痕关联。
     */
    record PolicyLoanDisbursementReceipt(
            String loanId,
            String policyId,
            BigDecimal loanAmount,
            BigDecimal interestRate,
            LocalDateTime loanDate) {
    }

    /**
     * 还款指令（保全域 → 保单域）：只表达「还哪笔、还多少、何时还」，分配由对端聚合现场计算。
     *
     * @param repaymentId 还款业务号（本域生成，全局唯一幂等键；同键异额由对端拒绝）
     * @param policyId 保单ID
     * @param loanId 目标贷款明细业务号
     * @param repaymentAmount 本次还款总额（是否超欠款总额由对端聚合核定）
     * @param repaymentDate 还款日期（本次计息区间终点）
     */
    record PolicyLoanRepaymentOrder(
            String repaymentId,
            String policyId,
            String loanId,
            BigDecimal repaymentAmount,
            LocalDateTime repaymentDate) {
    }

    /**
     * 还款回执（保单域 → 保全域）：承载对端权威分配与还款后余额，供收款、冲销与留痕关联。
     * <p>
     * 🔴 利息/本金拆分与还款后余额<b>一律以本回执为准</b>，调用方不得复算——同一笔还款在计费域冲销的
     * 利息/本金口径必须与保单域记账逐位一致，复算即产生第二份公式（必然漂移）。
     * </p>
     */
    record PolicyLoanRepaymentReceipt(
            String repaymentId,
            String policyId,
            String loanId,
            BigDecimal repaymentAmount,
            BigDecimal interestPortion,
            BigDecimal principalPortion,
            BigDecimal accruedInterest,
            BigDecimal outstandingPrincipalAfter,
            BigDecimal outstandingInterestAfter,
            boolean settled,
            LocalDateTime repaymentDate) {
    }
}
