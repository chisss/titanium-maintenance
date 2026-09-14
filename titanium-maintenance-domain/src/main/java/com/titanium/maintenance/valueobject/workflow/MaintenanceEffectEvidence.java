package com.titanium.maintenance.valueobject.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 生效请求及其可选成功回执。
 * <p>
 * 回执有两种<b>互斥</b>形态，取决于案件的生效出口：{@link #application}（保单交易出口，产出批单号与新保单版本）
 * 与 {@link #investmentSwitch}（投资账户转换出口，不改保单状态与字段）。同一任务不得同时具备两者——一笔生效
 * 只能由一个出口受理，双出口并存意味着同一事实被记了两遍账。
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
        MaintenanceInvestmentSwitchEvidence investmentSwitch) {

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
        if (application != null && investmentSwitch != null) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectEvidence", "investmentSwitch", "一笔生效只能由一个出口受理：保单回执与投资账户转换回执互斥");
        }
    }

    public static MaintenanceEffectEvidence requested(MaintenanceEffectRequestEvidence request) {
        return new MaintenanceEffectEvidence(request, null, null);
    }

    public MaintenanceEffectEvidence applied(MaintenancePolicyApplicationEvidence application) {
        return new MaintenanceEffectEvidence(request, application, null);
    }

    public MaintenanceEffectEvidence switched(MaintenanceInvestmentSwitchEvidence evidence) {
        return new MaintenanceEffectEvidence(request, null, evidence);
    }

    @JsonIgnore
    public boolean isApplied() {
        return application != null || investmentSwitch != null;
    }
}
