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
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

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

    /**
     * Policy 返回且已通过防腐层校验的字段目录证据。
     *
     * <p>🔴 {@code fallback} 与上游 {@code PolicyFieldCatalogResponse} 同名同义、同序：
     * 为 {@code true} 表示「请求的业务日期早于目录最早版本」，本次取到的是**最早版本**而非该时点的
     * 真实版本（上游的有意降级，不报错）。本域**不做二次判断也不据此拒绝**——它是上游的权威标记，
     * 消费方的职责是把它当降级信号显式可见（适配器已在 fallback 时记 warn），
     * 而不是在防腐层把它抹平。</p>
     */
    record PolicyFieldCatalogEvidence(
            String tenantId,
            String productType,
            String policyType,
            LocalDate businessDate,
            boolean fallback,
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

    /**
     * Maintenance 侧的字段描述证据。
     *
     * <p>🔴 分量与上游 {@code PolicyFieldDescriptorResponse} **逐位对齐**（含顺序），由
     * {@code PolicyFieldCatalogContractGuardTest} 机械守护——上游加分量而本域漏加时**编译不会失败**，
     * 只是该字段永远读不到（静默漂移）。
     *
     * <p>🔴 分析位只做**非空校验**，不复制 Policy 侧的四条业务不变量：其中两条依赖 valueType/collection
     * （属描述符层级而非能力位层级，规则权威已在 Policy 构造期 fail-closed），跨域复制会造出第二套实现，
     * 且本域没有承载这四条语义的错误码段。
     */
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
            LocalDate deprecatedAt,
            FieldAnalysisCapability analysis) {

        public PolicyFieldDescriptorEvidence {
            fieldCode = requireText(fieldCode, "字段编码");
            labelKey = requireText(labelKey, "字段标签键");
            objectIdentityField = normalize(objectIdentityField);
            if (objectType == null || valueType == null || capability == null
                    || sensitivity == null || maskingPolicy == null) {
                throw validation("字段类型、能力和敏感策略不能为空");
            }
            if (analysis == null) {
                throw validation("字段分析能力位缺失（Policy 契约漂移或反序列化丢字段）");
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
