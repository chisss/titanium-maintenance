package com.titanium.maintenance.valueobject.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 生效请求及其可选成功回执。
 * <p>
 * 回执有五种<b>互斥</b>形态，取决于案件的生效出口：{@link #application}（保单交易出口，产出批单号与新保单版本）、
 * {@link #investmentSwitch}（投资账户转换出口，不改保单状态与字段）、{@link #loanDisbursement}（保单贷款放款出口）、
 * {@link #loanRepayment}（保单贷款还款出口）与 {@link #policyReissue}（保单补发单证请求出口，产出对端的补发请求事实）。
 * 同一任务不得同时具备两者——一笔生效只能由一个出口受理，双出口并存意味着同一事实被记了两遍账。
 * </p>
 * <p>
 * ⚠️ 本类型是事件溯源聚合的持久化状态（存于事件流），新增组件字段对历史事件向后兼容：旧载荷缺该字段时
 * Jackson 按 null 注入，紧凑构造器接受「两者皆空」的已请求未回执态。
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceEffectEvidence(
        MaintenanceEffectRequestEvidence request,
        MaintenancePolicyApplicationEvidence application,
        MaintenanceInvestmentSwitchEvidence investmentSwitch,
        MaintenanceLoanDisbursementEvidence loanDisbursement,
        MaintenanceLoanRepaymentEvidence loanRepayment,
        MaintenancePolicyReissueEvidence policyReissue) {

    public MaintenanceEffectEvidence {
        if (request == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectEvidence", "request", "生效请求证据不能为空");
        }
        if (application != null
                && (!request.requestId().equals(application.requestId())
                        || request.expectedPolicyVersion() != application.expectedPolicyVersion())) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectEvidence", "application", "Policy 回执与生效请求不一致");
        }
        if (investmentSwitch != null && !request.requestId().equals(investmentSwitch.requestId())) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectEvidence", "investmentSwitch", "投资账户转换回执与生效请求不一致");
        }
        if (loanDisbursement != null && !request.requestId().equals(loanDisbursement.requestId())) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectEvidence", "loanDisbursement", "保单贷款放款回执与生效请求不一致");
        }
        if (loanRepayment != null && !request.requestId().equals(loanRepayment.requestId())) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectEvidence", "loanRepayment", "保单贷款还款回执与生效请求不一致");
        }
        if (policyReissue != null && !request.requestId().equals(policyReissue.requestId())) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectEvidence", "policyReissue", "保单补发请求回执与生效请求不一致");
        }
        int exits = (application == null ? 0 : 1) + (investmentSwitch == null ? 0 : 1)
                + (loanDisbursement == null ? 0 : 1) + (loanRepayment == null ? 0 : 1)
                + (policyReissue == null ? 0 : 1);
        if (exits > 1) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectEvidence", "loanRepayment",
                    "一笔生效只能由一个出口受理：保单回执、投资账户转换回执、贷款放款回执、贷款还款回执与"
                            + "补发请求回执互斥");
        }
    }

    public static MaintenanceEffectEvidence requested(MaintenanceEffectRequestEvidence request) {
        return new MaintenanceEffectEvidence(request, null, null, null, null, null);
    }

    public MaintenanceEffectEvidence applied(MaintenancePolicyApplicationEvidence application) {
        return new MaintenanceEffectEvidence(request, application, null, null, null, null);
    }

    public MaintenanceEffectEvidence switched(MaintenanceInvestmentSwitchEvidence evidence) {
        return new MaintenanceEffectEvidence(request, null, evidence, null, null, null);
    }

    public MaintenanceEffectEvidence disbursed(MaintenanceLoanDisbursementEvidence evidence) {
        return new MaintenanceEffectEvidence(request, null, null, evidence, null, null);
    }

    public MaintenanceEffectEvidence repaid(MaintenanceLoanRepaymentEvidence evidence) {
        return new MaintenanceEffectEvidence(request, null, null, null, evidence, null);
    }

    /** 记入补发请求回执（保单补发出口的唯一收口形态，与其余四个出口互斥）。 */
    public MaintenanceEffectEvidence reissued(MaintenancePolicyReissueEvidence evidence) {
        return new MaintenanceEffectEvidence(request, null, null, null, null, evidence);
    }

    @JsonIgnore
    public boolean isApplied() {
        return application != null || investmentSwitch != null
                || loanDisbursement != null || loanRepayment != null || policyReissue != null;
    }
}
