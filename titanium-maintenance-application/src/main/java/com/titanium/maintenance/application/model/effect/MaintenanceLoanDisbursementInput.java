package com.titanium.maintenance.application.model.effect;

import java.math.BigDecimal;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 保单贷款（POLICY_LOAN）生效请求的申请金额参数。
 * <p>
 * 🔴 <b>参数为何随生效请求传入、而非取自案件读模型</b>：与 {@link MaintenanceInvestmentSwitchInput}
 * 同规——本域无每案贷款参数承载（字段变更表经保单字段目录权威校验，申请金额不在目录内，无法经
 * 提案通路落库），故由生效调用方当次提供，摘要冻结进通用生效请求证据（{@code MaintenanceEffectRequestEvidence}），
 * 重试时凭摘要校验原载荷一致（申请金额变更即拒绝重试，防止同键异额串号）。
 * </p>
 * <p>
 * 连带约束：{@code applyScheduled}（计划调度）无调用方可传参，故贷款类案件<b>不支持未来生效调度</b>，
 * 遇调度入口即拒绝（fail-closed，与账户转换同规）。
 * </p>
 *
 * @param requestedAmount 申请贷款金额（须大于 0，是否超可贷上限由领域服务核定）
 */
public record MaintenanceLoanDisbursementInput(
        BigDecimal requestedAmount) {

    public MaintenanceLoanDisbursementInput {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MaintenanceValidationException(
                    "MaintenanceLoanDisbursementInput", "requestedAmount", "申请贷款金额必须大于0");
        }
    }
}
