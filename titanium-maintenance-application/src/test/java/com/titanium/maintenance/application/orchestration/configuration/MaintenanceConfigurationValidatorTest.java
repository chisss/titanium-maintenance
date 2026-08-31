package com.titanium.maintenance.application.orchestration.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.configuration.control.MaintenanceAccessRule;
import com.titanium.maintenance.configuration.control.MaintenanceChannelCapability;
import com.titanium.maintenance.configuration.control.MaintenanceFeeRule;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.configuration.control.MaintenanceOutputRule;
import com.titanium.maintenance.port.MaintenanceConfigurationReferencePort;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldCapabilityEvidence;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

class MaintenanceConfigurationValidatorTest {

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
                PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE, null);
        return new PolicyFieldCatalogEvidence("tenant-1", "LIFE", "INDIVIDUAL",
                BUSINESS_DATE, "catalog-v1", "a".repeat(64), List.of(field));
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
                List.of(), Set.of("RULE_CONTACT"), "APPROVAL_STANDARD", MaintenanceFeeRule.none(),
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
