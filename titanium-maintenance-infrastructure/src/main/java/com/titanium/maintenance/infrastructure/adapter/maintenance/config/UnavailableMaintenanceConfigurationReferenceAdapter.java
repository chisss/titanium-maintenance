package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import com.titanium.maintenance.port.ruleengine.MaintenanceConfigurationReferencePort;

/**
 * 未装配任何引用校验适配器时使用的失败关闭后备实现。
 * <p>
 * 🔴 正常装配下<b>本类不会被实例化</b>：规则权威适配器
 * {@code RuleEngineConfigurationReferenceAdapter} 是无条件 {@code @Component}，
 * 由其经 {@code @ConditionalOnMissingBean} 顶掉本后备 Bean。
 * 保留它的唯一理由是「容器里确实一个实现都没有」这一异常装配态——
 * 此时保全配置发布必须<b>失败关闭</b>（返回非权威证据 ⇒ 校验器抛
 * {@code MaintenanceConfigurationDependencyException}），而不是静默放行未校验的配置。
 * </p>
 */
public class UnavailableMaintenanceConfigurationReferenceAdapter
        implements MaintenanceConfigurationReferencePort {

    @Override
    public ReferenceValidationEvidence validate(ReferenceValidationRequest request) {
        return ReferenceValidationEvidence.unavailable(
                "保全配置引用校验适配器未装配（规则引擎权威与权限/模板目录均不可用）");
    }
}
