package com.titanium.maintenance.application.model.effect;

import java.math.BigDecimal;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 保单贷款还款（POLICY_LOAN_REPAYMENT）生效请求的还款参数（g04-04）。
 * <p>
 * 🔴 <b>只传「还哪笔、还多少」，不传拆分</b>：利息/本金拆分（先息后本）与还款后余额由保单域贷款账户聚合
 * 按持有的全量还款记录现场计算——它才是持有该贷款状态的一方。调用方复算即产生第二份公式，必然与保单域
 * 记账漂移，故本类型只有两个字段。
 * </p>
 * <p>
 * 🔴 <b>参数为何随生效请求传入、而非取自案件读模型</b>：与 {@link MaintenanceLoanDisbursementInput} 同规
 * ——本域无每案还款参数承载（字段变更表经保单字段目录权威校验，还款要素不在目录内，无法经提案通路落库），
 * 故由生效调用方当次提供，摘要冻结进通用生效请求证据（{@code MaintenanceEffectRequestEvidence}），
 * 重试时凭摘要校验原载荷一致（金额或目标明细变更即拒绝重试，防止同键异额串号）。
 * </p>
 * <p>
 * 🔴 <b>目标贷款为何由调用方指名、不做「唯一未结清即选中」的推断</b>：同一保单可先后发生多笔贷款
 * （还清后再贷），「还哪笔」是业务事实而非可推导项；且还款额可以小于欠款（部分还款），即便只有一笔
 * 未结清贷款，金额与目标的对应关系也须由发起方明确。指名错误由保单域以「贷款明细不存在」拒绝
 * （失败关闭），不存在静默错记。
 * </p>
 * <p>
 * 连带约束：{@code applyScheduled}（计划调度）无调用方可传参，故贷款类案件<b>不支持未来生效调度</b>，
 * 遇调度入口即拒绝（fail-closed，与账户转换、放款同规）。
 * </p>
 *
 * @param loanId 目标贷款明细业务号（须属于本保单，存在性与归属由保单域核定）
 * @param repaymentAmount 本次还款总额（须大于 0，是否超欠款总额由保单域聚合核定）
 */
public record MaintenanceLoanRepaymentInput(
        String loanId,
        BigDecimal repaymentAmount) {

    public MaintenanceLoanRepaymentInput {
        if (loanId == null || loanId.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceLoanRepaymentInput", "loanId", "目标贷款明细业务号不能为空");
        }
        loanId = loanId.trim();
        if (repaymentAmount == null || repaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MaintenanceValidationException(
                    "MaintenanceLoanRepaymentInput", "repaymentAmount", "还款金额必须大于0");
        }
    }
}
