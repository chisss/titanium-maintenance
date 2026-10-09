package com.titanium.maintenance.port.ruleengine;

import java.util.Set;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 校验保全配置所引用规则、权限与模板的出口端口（对端域：rule-engine）。
 * <p>
 * 🔴 <b>权威来源分层</b>（G09 批次，2026-10-09）：
 * </p>
 * <ul>
 *   <li><b>规则码</b>：权威在 <b>rule-engine</b>——规则集「存在 + 已激活（{@code RuleSetStatus.ACTIVE}）+
 *       类型为 {@code RuleSetType.MAINTENANCE}」，由 {@code RuleEngineConfigurationReferenceAdapter}
 *       调其只读契约判定。
 *       此前这里读的是保全域内的<b>硬编码白名单</b>（{@code LocalMaintenanceConfigurationReferenceAdapter}），
 *       白名单与真实规则集必然漂移；该实现已删除，字面量清零。</li>
 *   <li><b>权限码 / 模板码</b>：本域自管目录（{@code MaintenanceReferenceCatalog}）——全仓<b>不存在</b>
 *       跨域权限目录或模板查询的只读契约，故不经空壳权威透传（透传等于「不校验」，是静默放宽）。
 *       目录取值为保全项访问规则与凭单/通知/归档模板的既有取值，行为与改造前的白名单一致。</li>
 * </ul>
 * <p>
 * 端口位置：{@code domain/port/ruleengine}（与 {@code aggregate} 平级、按对端域分包，
 * 见项目 CLAUDE.md §3.4.6 / §3.4.13）。原本位于 {@code port/maintenance} 子包，
 * 因其语义是「外部权威引用注册表」而非本域自管能力，随规则权威接入一并归位。
 * </p>
 */
public interface MaintenanceConfigurationReferencePort {

    /** 获取引用校验证据；提供端不可用时必须返回非权威结果或抛出依赖异常。 */
    ReferenceValidationEvidence validate(ReferenceValidationRequest request);

    /** 待校验引用集合。 */
    record ReferenceValidationRequest(String tenantId, Set<String> ruleCodes,
            Set<String> permissionCodes, Set<String> templateCodes) {

        public ReferenceValidationRequest {
            tenantId = requireText("tenantId", tenantId);
            ruleCodes = immutableCodes("ruleCodes", ruleCodes);
            permissionCodes = immutableCodes("permissionCodes", permissionCodes);
            templateCodes = immutableCodes("templateCodes", templateCodes);
        }
    }

    /** 权威提供端返回的引用解析证据。 */
    record ReferenceValidationEvidence(boolean authoritative, String evidenceVersion,
            Set<String> resolvedRuleCodes, Set<String> resolvedPermissionCodes,
            Set<String> resolvedTemplateCodes, String unavailableReason) {

        public ReferenceValidationEvidence {
            evidenceVersion = normalize(evidenceVersion);
            resolvedRuleCodes = immutableCodes("resolvedRuleCodes", resolvedRuleCodes);
            resolvedPermissionCodes = immutableCodes("resolvedPermissionCodes", resolvedPermissionCodes);
            resolvedTemplateCodes = immutableCodes("resolvedTemplateCodes", resolvedTemplateCodes);
            unavailableReason = normalize(unavailableReason);
            if (authoritative && evidenceVersion == null) {
                throw validation("evidenceVersion", "权威引用证据必须包含版本");
            }
            if (!authoritative && unavailableReason == null) {
                throw validation("unavailableReason", "非权威引用结果必须说明原因");
            }
        }

        /** 创建外部引用注册表不可用的失败关闭结果。 */
        public static ReferenceValidationEvidence unavailable(String reason) {
            return new ReferenceValidationEvidence(
                    false, null, Set.of(), Set.of(), Set.of(), reason);
        }
    }

    private static Set<String> immutableCodes(String fieldName, Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw validation(fieldName, "引用编码集合不能包含空项");
        }
        return values.stream().map(String::trim).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceConfigurationReference", fieldName, message);
    }
}
