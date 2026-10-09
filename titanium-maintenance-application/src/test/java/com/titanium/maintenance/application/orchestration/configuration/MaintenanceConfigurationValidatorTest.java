package com.titanium.maintenance.application.orchestration.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationCriteria;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationResult;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationDependencyException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationValidationException;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.configuration.control.MaintenanceAccessRule;
import com.titanium.maintenance.configuration.control.MaintenanceChannelCapability;
import com.titanium.maintenance.configuration.control.MaintenanceFeeRule;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.configuration.control.MaintenanceOutputRule;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCapabilityEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.maintenance.port.ruleengine.MaintenanceConfigurationReferencePort;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

class MaintenanceConfigurationValidatorTest {

    /** 夹具引用的规则码：definition(...) 与差集断言共用一处定义，避免期望值内联副本 */
    private static final String APPROVAL_RULE_CODE = "APPROVAL_STANDARD";

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 24);
    private static final LocalDateTime VALIDATED_AT = LocalDateTime.of(2026, 8, 24, 12, 0);

    @Test
    void shouldValidateFieldsAndAllReferencedCodes() {
        MaintenanceConfigurationValidator validator = new MaintenanceConfigurationValidator(
                request -> catalog(true, true, false, PolicyFieldValueType.TEXT),
                request -> new MaintenanceConfigurationReferencePort.ReferenceValidationEvidence(
                        true, "reference-v1", request.ruleCodes(), request.permissionCodes(),
                        request.templateCodes(), null));

        MaintenanceConfigurationValidationResult result = validator.validate(
                "tenant-1", definition(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)),
                criteria(), VALIDATED_AT);

        assertTrue(result.valid());
        assertEquals("catalog-v1", result.catalogVersion());
        assertEquals("reference-v1", result.referenceEvidenceVersion());
    }

    @Test
    void shouldReportFieldTypeProposalAndClearCapabilityProblemsTogether() {
        MaintenanceConfigurationValidator validator = new MaintenanceConfigurationValidator(
                request -> catalog(false, false, false, PolicyFieldValueType.INTEGER),
                this::resolveAllReferences);

        MaintenanceConfigurationValidationResult result = validator.validate(
                "tenant-1", definition(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)),
                criteria(), VALIDATED_AT);

        Set<String> issueCodes = result.issues().stream()
                .map(MaintenanceConfigurationValidationResult.ValidationIssue::code)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("FIELD_NOT_PROPOSABLE", "FIELD_NOT_CLEARABLE", "FIELD_TYPE_MISMATCH"), issueCodes);
    }

    @Test
    void shouldFailClosedWhenReferenceRegistryIsUnavailable() {
        MaintenanceConfigurationValidator validator = new MaintenanceConfigurationValidator(
                request -> catalog(true, true, true, PolicyFieldValueType.TEXT),
                request -> MaintenanceConfigurationReferencePort.ReferenceValidationEvidence
                        .unavailable("registry down"));

        assertThrows(MaintenanceConfigurationDependencyException.class,
                () -> validator.validate("tenant-1", definition(MaintenanceFieldRule.editable(
                                "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT)),
                        criteria(), VALIDATED_AT));
    }

    @Test
    void shouldRejectWhenReferencedRuleCodeIsNotResolved() {
        // 权威应答只解析出权限与模板、规则码全部落空 —— AC-03「反」：引用不存在的规则码 ⇒ 发布被拒
        MaintenanceConfigurationValidator validator = new MaintenanceConfigurationValidator(
                request -> catalog(true, true, true, PolicyFieldValueType.TEXT),
                request -> new MaintenanceConfigurationReferencePort.ReferenceValidationEvidence(
                        true, "reference-v1", Set.of(), request.permissionCodes(),
                        request.templateCodes(), null));
        MaintenanceItemDefinition itemDefinition = definition(MaintenanceFieldRule.editable(
                "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT));

        MaintenanceConfigurationValidationResult result = validator.validate(
                "tenant-1", itemDefinition, criteria(), VALIDATED_AT);

        assertFalse(result.valid(), "权威解析不出被引用的规则码时配置不得发布");
        assertTrue(result.issues().stream()
                        .map(MaintenanceConfigurationValidationResult.ValidationIssue::code)
                        .allMatch("RULE_NOT_FOUND"::equals),
                "规则码差集只应落成 RULE_NOT_FOUND，混入其它类别即说明被拒原因不是引用缺失");
        assertTrue(result.issues().stream()
                        .map(MaintenanceConfigurationValidationResult.ValidationIssue::field)
                        .allMatch("ruleCodes"::equals), "差集问题须落在 ruleCodes 字段上");
        assertTrue(result.issues().stream()
                        .map(MaintenanceConfigurationValidationResult.ValidationIssue::message)
                        .anyMatch(message -> message.contains(APPROVAL_RULE_CODE)),
                "被引用的规则码须逐条点名（引用不存在: " + APPROVAL_RULE_CODE + "）");
        // 🔴「发布被拒」的落点：抛**业务校验异常**——不得升级为依赖不可用异常
        // （技术故障与业务拒绝必须可分，D13 范式）
        assertThrows(MaintenanceConfigurationValidationException.class,
                () -> validator.validateAndRequire("tenant-1", itemDefinition, criteria(), VALIDATED_AT));
    }

    @Test
    void shouldRequireSensitiveFieldViewPermission() {
        MaintenanceConfigurationValidator validator = new MaintenanceConfigurationValidator(
                request -> catalog(true, true, true, PolicyFieldValueType.TEXT),
                this::resolveAllReferences);

        MaintenanceConfigurationValidationResult result = validator.validate(
                "tenant-1", definition(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true, PolicyFieldValueType.TEXT), false),
                criteria(), VALIDATED_AT);

        assertEquals("SENSITIVE_FIELD_PERMISSION_REQUIRED", result.issues().getFirst().code());
    }

    @Test
    void shouldRequireExplicitExpectedFieldType() {
        MaintenanceConfigurationValidator validator = new MaintenanceConfigurationValidator(
                request -> catalog(true, true, true, PolicyFieldValueType.TEXT),
                this::resolveAllReferences);

        MaintenanceConfigurationValidationResult result = validator.validate(
                "tenant-1", definition(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", true, true)), criteria(), VALIDATED_AT);

        assertEquals("FIELD_TYPE_EXPECTATION_REQUIRED", result.issues().getFirst().code());
    }

    @Test
    void shouldRequireEditableFieldWhenWorkflowContainsDataEntry() {
        MaintenanceConfigurationValidator validator = new MaintenanceConfigurationValidator(
                request -> catalog(true, true, true, PolicyFieldValueType.TEXT),
                this::resolveAllReferences);
        MaintenanceFieldRule visibleOnly = new MaintenanceFieldRule(
                "policy.holder.mobile", false, true, false, false, null,
                PolicyFieldValueType.TEXT);

        MaintenanceConfigurationValidationResult result = validator.validate(
                "tenant-1", definition(visibleOnly), criteria(), VALIDATED_AT);

        assertEquals("DATA_ENTRY_FIELD_REQUIRED", result.issues().getFirst().code());
    }

    private MaintenanceConfigurationReferencePort.ReferenceValidationEvidence resolveAllReferences(
            MaintenanceConfigurationReferencePort.ReferenceValidationRequest request) {
        return new MaintenanceConfigurationReferencePort.ReferenceValidationEvidence(
                true, "reference-v1", request.ruleCodes(), request.permissionCodes(),
                request.templateCodes(), null);
    }

    private PolicyFieldCatalogEvidence catalog(boolean proposable, boolean clearable,
            boolean executionSupported, PolicyFieldValueType valueType) {
        PolicyFieldCapabilityEvidence capability = new PolicyFieldCapabilityEvidence(
                true, proposable, clearable, executionSupported, false,
                proposable ? "POLICY_INFO_CHANGE" : null);
        PolicyFieldDescriptorEvidence field = new PolicyFieldDescriptorEvidence(
                "policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER, valueType,
                "policy.field.holder.mobile", false, null, capability,
                PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE, null,
                new FieldAnalysisCapability(true, false, false, true));
        return new PolicyFieldCatalogEvidence("tenant-1", "LIFE", "INDIVIDUAL",
                BUSINESS_DATE, false, "catalog-v1", "a".repeat(64), List.of(field));
    }

    private MaintenanceItemDefinition definition(MaintenanceFieldRule fieldRule) {
        return definition(fieldRule, true);
    }

    private MaintenanceItemDefinition definition(
            MaintenanceFieldRule fieldRule, boolean includeSensitivePermission) {
        Set<String> viewPermissions = includeSensitivePermission
                ? Set.of("maintenance:item:view", "maintenance:sensitive:view")
                : Set.of("maintenance:item:view");
        MaintenanceItemControls controls = new MaintenanceItemControls(
                Set.of(MaintenanceChannelCapability.manualApproval(MaintenanceChannel.MANUAL)),
                List.of(), Set.of("RULE_CONTACT"), APPROVAL_RULE_CODE, MaintenanceFeeRule.none(),
                new MaintenanceAccessRule(Set.of("maintenance:item:operate"),
                        viewPermissions),
                new MaintenanceOutputRule("VOUCHER_CONTACT", Set.of("NOTICE_SMS"), "ARCHIVE_POLICY"));
        return new MaintenanceItemDefinition("CONTACT_CHANGE", "1.0.0", "联系方式变更",
                MaintenanceItemCategory.BASIC_INFORMATION, Set.of(MaintenanceChannel.MANUAL),
                List.of(fieldRule), List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, new MaintenanceEffectiveRule(
                        Set.of(EffectiveTimeType.IMMEDIATE), EffectiveTimeType.IMMEDIATE, 0, 0),
                Set.of(), true, controls);
    }

    private MaintenanceConfigurationValidationCriteria criteria() {
        return new MaintenanceConfigurationValidationCriteria("LIFE", "INDIVIDUAL", BUSINESS_DATE);
    }
}
