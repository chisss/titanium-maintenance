package com.titanium.maintenance.port.policy;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

/** Maintenance 消费 Policy 字段目录的出口端口。 */
public interface PolicyFieldCatalogPort {

    /** 获取指定业务时点的完整字段目录证据。 */
    PolicyFieldCatalogEvidence getCatalog(PolicyFieldCatalogRequest request);

    /** 字段目录查询请求。 */
    record PolicyFieldCatalogRequest(
            String tenantId, String productType, String policyType, LocalDate businessDate) {

        public PolicyFieldCatalogRequest {
            tenantId = requireText(tenantId, "租户ID");
            productType = normalize(productType);
            policyType = normalize(policyType);
            if (businessDate == null) {
                throw validation("业务日期不能为空");
            }
        }
    }

    /** Policy 返回且已通过防腐层校验的字段目录证据。 */
    record PolicyFieldCatalogEvidence(
            String tenantId,
            String productType,
            String policyType,
            LocalDate businessDate,
            String catalogVersion,
            String contentHash,
            List<PolicyFieldDescriptorEvidence> fields) {

        private static final Pattern SHA_256_PATTERN = Pattern.compile("[a-f0-9]{64}");

        public PolicyFieldCatalogEvidence {
            tenantId = requireText(tenantId, "租户ID");
            productType = normalize(productType);
            policyType = normalize(policyType);
            catalogVersion = requireText(catalogVersion, "目录版本");
            contentHash = requireText(contentHash, "目录内容哈希").toLowerCase();
            if (businessDate == null) {
                throw validation("目录业务日期不能为空");
            }
            if (!SHA_256_PATTERN.matcher(contentHash).matches()) {
                throw validation("目录内容哈希不是合法SHA-256");
            }
            if (fields == null || fields.isEmpty()) {
                throw validation("字段目录不能为空");
            }
            if (fields.stream().anyMatch(field -> field == null)) {
                throw validation("字段目录不能包含空字段");
            }
            fields = fields.stream()
                    .sorted(Comparator.comparing(PolicyFieldDescriptorEvidence::fieldCode))
                    .toList();
            validateUniqueFields(fields);
        }

        /** 按字段码查找目录证据。 */
        public PolicyFieldDescriptorEvidence requireField(String fieldCode) {
            return fields.stream()
                    .filter(field -> field.fieldCode().equals(fieldCode))
                    .findFirst()
                    .orElseThrow(() -> validation("Policy 字段目录不存在字段: " + fieldCode));
        }

        private static void validateUniqueFields(List<PolicyFieldDescriptorEvidence> fields) {
            Set<String> codes = new HashSet<>();
            for (PolicyFieldDescriptorEvidence field : fields) {
                if (!codes.add(field.fieldCode())) {
                    throw validation("Policy 字段目录编码重复: " + field.fieldCode());
                }
            }
        }
    }

    /** Maintenance 侧的字段描述证据。 */
    record PolicyFieldDescriptorEvidence(
            String fieldCode,
            PolicyFieldObjectType objectType,
            PolicyFieldValueType valueType,
            String labelKey,
            boolean collection,
            String objectIdentityField,
            PolicyFieldCapabilityEvidence capability,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy,
            LocalDate deprecatedAt) {

        public PolicyFieldDescriptorEvidence {
            fieldCode = requireText(fieldCode, "字段编码");
            labelKey = requireText(labelKey, "字段标签键");
            objectIdentityField = normalize(objectIdentityField);
            if (objectType == null || valueType == null || capability == null
                    || sensitivity == null || maskingPolicy == null) {
                throw validation("字段类型、能力和敏感策略不能为空");
            }
            if (collection && objectIdentityField == null) {
                throw validation("集合字段必须配置稳定业务对象标识");
            }
            if (!collection && objectIdentityField != null) {
                throw validation("非集合字段不能配置业务对象标识");
            }
            if (capability.requiresObjectId() && !collection) {
                throw validation("仅集合字段可以要求业务对象标识");
            }
            if (sensitivity.requiresMasking() && maskingPolicy == PolicyFieldMaskingPolicy.NONE) {
                throw validation("敏感字段必须配置掩码策略");
            }
        }
    }

    /** Maintenance 侧的字段能力证据。 */
    record PolicyFieldCapabilityEvidence(
            boolean readable,
            boolean proposable,
            boolean clearable,
            boolean executionSupported,
            boolean requiresObjectId,
            String changeTypeCode) {

        public PolicyFieldCapabilityEvidence {
            changeTypeCode = normalize(changeTypeCode);
            if (clearable && !proposable) {
                throw validation("可清空字段必须允许提交变更提案");
            }
            if (executionSupported && !proposable) {
                throw validation("可执行字段必须允许提交变更提案");
            }
            if (proposable && changeTypeCode == null) {
                throw validation("可提案字段必须配置业务变更类别");
            }
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw validation(label + "不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException validation(String message) {
        return new MaintenanceValidationException("PolicyFieldCatalog", message);
    }
}
