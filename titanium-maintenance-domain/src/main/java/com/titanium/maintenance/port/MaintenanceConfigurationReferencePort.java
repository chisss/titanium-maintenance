package com.titanium.maintenance.port;

import java.util.Set;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 校验保全配置所引用规则、权限与模板的出口端口。 */
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
