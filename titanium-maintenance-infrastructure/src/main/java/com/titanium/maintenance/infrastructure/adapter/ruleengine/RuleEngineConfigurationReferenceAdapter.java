package com.titanium.maintenance.infrastructure.adapter.ruleengine;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.constant.MaintenanceReferenceCatalog;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationDependencyException;
import com.titanium.maintenance.infrastructure.client.ruleengine.RuleEngineClient;
import com.titanium.maintenance.port.ruleengine.MaintenanceConfigurationReferencePort;
import com.titanium.metadata.enums.ruleengine.RuleSetStatus;
import com.titanium.metadata.enums.ruleengine.RuleSetType;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.ruleengine.api.response.ruleset.RuleSetResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保全配置引用校验适配器：规则码权威取自 rule-engine（六边形架构 driven adapter，AC-03）
 * <p>
 * {@link MaintenanceConfigurationReferencePort} 的真实实现。保存/发布保全配置时，配置里引用的<b>规则码</b>
 * 由本适配器调规则引擎只读契约判定，判据为三元组：
 * <b>存在</b>（编码在册） + <b>已激活</b>（{@code RuleSetStatus.ACTIVE}） + <b>类型为保全</b>
 * （{@code RuleSetType.MAINTENANCE}）。
 * </p>
 * <p>
 * 🔴 <b>替换的旧实现</b>：{@code LocalMaintenanceConfigurationReferenceAdapter}（硬编码规则码白名单——
 * {@code APPROVAL_STANDARD} / {@code MAINTENANCE_PREMIUM_FORMULA} / {@code MAINTENANCE_SETTLEMENT_GATE}
 * 三项常量，无任何权威依据）——白名单与真实规则集必然漂移：规则引擎侧新建/停用规则集，
 * 本域配置校验照旧放行或照旧拒绝，且无任何信号。该实现已删除，非测试源码中不再存在该白名单字面量。
 * </p>
 * <p>
 * 🔴 <b>一次拉全量、不做 N+1</b>：{@code listRuleSets("MAINTENANCE")} 一次取回该类型全部规则集，
 * 请求集合在<b>本地求交</b>得到「已解析」集合。差集（请求了但不在册/未激活）由
 * {@code MaintenanceConfigurationValidator} 落成 {@code RULE_NOT_FOUND} 校验问题。
 * </p>
 * <p>
 * 🔴 <b>响应侧再过滤一次</b>：对端按类型查询是服务端行为，本适配器<b>不信任</b>「返回值必然同类型且已激活」，
 * 仍按其自报的 {@code ruleSetType}/{@code status} 过滤——权威判定的依据必须是响应里可核对的事实，
 * 而不是对服务端过滤逻辑的假设。
 * </p>
 * <p>
 * 🔴 <b>权限码 / 模板码仍取本域目录</b>（{@link MaintenanceReferenceCatalog}）：全仓不存在跨域权限目录或
 * 模板查询的只读契约，造空壳权威等于把「不校验」包装成「已校验」。理由与边界见该常量类与端口 javadoc。
 * </p>
 * <p>
 * <b>失败语义</b>：规则引擎不可达 / 非成功码 / 载荷无效一律抛
 * {@link MaintenanceConfigurationDependencyException}（错误码 71002004，Web 层映射 HTTP 502）——
 * 配置发布<b>失败关闭</b>，不得降级为「视为存在」（否则配置带着不存在的规则上线）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngineConfigurationReferenceAdapter implements MaintenanceConfigurationReferencePort {

    /** 证据版本：标识「规则权威取自 rule-engine 只读契约」这一事实及其口径 */
    static final String EVIDENCE_VERSION = "rule-engine-ruleset-registry-v1";

    private final RuleEngineClient ruleEngineClient;

    @Override
    public ReferenceValidationEvidence validate(ReferenceValidationRequest request) {
        Set<String> activeRuleCodes = loadActiveMaintenanceRuleCodes(request.tenantId());
        return new ReferenceValidationEvidence(
                true,
                EVIDENCE_VERSION,
                intersect(request.ruleCodes(), activeRuleCodes),
                intersect(request.permissionCodes(), MaintenanceReferenceCatalog.PERMISSION_CODES),
                intersect(request.templateCodes(), MaintenanceReferenceCatalog.TEMPLATE_CODES),
                null);
    }

    /** 拉取该租户在册且已激活的保全规则集编码；任一步无效即失败关闭 */
    private Set<String> loadActiveMaintenanceRuleCodes(String tenantId) {
        ApiResponse<List<RuleSetResponse>> response;
        try {
            response = ruleEngineClient.listRuleSets(RuleSetType.MAINTENANCE.getCode());
        } catch (Exception exception) {
            log.warn("规则引擎规则集查询不可达, tenantId={}, 原因={}", tenantId, exception.getMessage(), exception);
            throw new MaintenanceConfigurationDependencyException(
                    "规则引擎规则集查询不可达（服务不可达），无法校验配置引用的规则码: " + exception.getClass().getSimpleName());
        }
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new MaintenanceConfigurationDependencyException(
                    "规则引擎规则集查询未返回有效结果, code=" + (response == null ? "null" : response.getCode()));
        }
        return response.getData().stream()
                .filter(ruleSet -> ruleSet != null && ruleSet.getRuleSetCode() != null
                        && RuleSetType.MAINTENANCE == ruleSet.getRuleSetType()
                        && RuleSetStatus.ACTIVE == ruleSet.getStatus())
                .map(RuleSetResponse::getRuleSetCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 请求集合 ∩ 权威集合（差集由校验器落成 RULE_NOT_FOUND / PERMISSION_NOT_FOUND / TEMPLATE_NOT_FOUND） */
    private Set<String> intersect(Set<String> requested, Set<String> authoritative) {
        return requested.stream().filter(authoritative::contains).collect(Collectors.toUnmodifiableSet());
    }
}
