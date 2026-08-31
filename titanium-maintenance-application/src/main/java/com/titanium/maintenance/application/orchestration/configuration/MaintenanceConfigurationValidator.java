package com.titanium.maintenance.application.orchestration.configuration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationCriteria;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationResult;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationResult.ValidationIssue;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationDependencyException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationValidationException;
import com.titanium.maintenance.common.exception.PolicyFieldCatalogUnavailableException;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.port.MaintenanceConfigurationReferencePort;
import com.titanium.maintenance.port.MaintenanceConfigurationReferencePort.ReferenceValidationEvidence;
import com.titanium.maintenance.port.MaintenanceConfigurationReferencePort.ReferenceValidationRequest;
import com.titanium.maintenance.port.PolicyFieldCatalogPort;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldCatalogRequest;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;

import lombok.RequiredArgsConstructor;

/** 编排 Policy 字段目录与外部引用注册表的配置发布校验。 */
@Component
@RequiredArgsConstructor
public class MaintenanceConfigurationValidator {

    private static final String SENSITIVE_FIELD_VIEW_PERMISSION = "maintenance:sensitive:view";

    private final PolicyFieldCatalogPort policyFieldCatalogPort;
    private final MaintenanceConfigurationReferencePort referencePort;

    /** 返回完整校验问题；权威依赖不可用时失败关闭。 */
    public MaintenanceConfigurationValidationResult validate(String tenantId,
            MaintenanceItemDefinition definition, MaintenanceConfigurationValidationCriteria criteria,
            LocalDateTime validatedAt) {
        PolicyFieldCatalogEvidence catalog = getCatalog(tenantId, criteria);
        ReferenceSets references = referencesOf(definition);
        ReferenceValidationEvidence referenceEvidence = validateReferences(tenantId, references);
        if (referenceEvidence == null || !referenceEvidence.authoritative()) {
            String reason = referenceEvidence == null
                    ? "引用注册表未返回校验证据"
                    : referenceEvidence.unavailableReason();
            throw new MaintenanceConfigurationDependencyException("保全配置引用校验不可用: " + reason);
        }

        List<ValidationIssue> issues = new ArrayList<>();
        validateDataEntryFields(definition, issues);
        validateFields(definition, criteria, catalog, issues);
        addMissingReferences("RULE_NOT_FOUND", "ruleCodes", references.rules(),
                referenceEvidence.resolvedRuleCodes(), issues);
        addMissingReferences("PERMISSION_NOT_FOUND", "permissionCodes", references.permissions(),
                referenceEvidence.resolvedPermissionCodes(), issues);
        addMissingReferences("TEMPLATE_NOT_FOUND", "templateCodes", references.templates(),
                referenceEvidence.resolvedTemplateCodes(), issues);

        return new MaintenanceConfigurationValidationResult(issues.isEmpty(), issues,
                catalog.catalogVersion(), catalog.contentHash(), referenceEvidence.evidenceVersion(), validatedAt);
    }

    private void validateDataEntryFields(
            MaintenanceItemDefinition definition, List<ValidationIssue> issues) {
        boolean dataEntryRequired = definition.steps().stream()
                .anyMatch(step -> step.stepType() == MaintenanceStepType.DATA_ENTRY);
        boolean hasEditableField = definition.fieldRules().stream().anyMatch(MaintenanceFieldRule::editable);
        if (dataEntryRequired && !hasEditableField) {
            issues.add(issue("DATA_ENTRY_FIELD_REQUIRED", "fieldRules",
                    "包含信息录入步骤时必须配置至少一个可编辑字段"));
        }
    }

    /** 校验并在存在业务问题时阻止状态流转。 */
    public MaintenanceConfigurationValidationResult validateAndRequire(String tenantId,
            MaintenanceItemDefinition definition, MaintenanceConfigurationValidationCriteria criteria,
            LocalDateTime validatedAt) {
        MaintenanceConfigurationValidationResult result = validate(tenantId, definition, criteria, validatedAt);
        if (!result.valid()) {
            String message = result.issues().stream()
                    .map(issue -> issue.code() + ":" + issue.field() + ":" + issue.message())
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("保全项配置校验失败");
            throw new MaintenanceConfigurationValidationException(message);
        }
        return result;
    }

    private PolicyFieldCatalogEvidence getCatalog(
            String tenantId, MaintenanceConfigurationValidationCriteria criteria) {
        try {
            PolicyFieldCatalogEvidence catalog = policyFieldCatalogPort.getCatalog(
                    new PolicyFieldCatalogRequest(tenantId, criteria.productType(),
                            criteria.policyType(), criteria.businessDate()));
            if (catalog == null) {
                throw new MaintenanceConfigurationDependencyException("Policy 字段目录未返回校验证据");
            }
            return catalog;
        } catch (PolicyFieldCatalogUnavailableException exception) {
            throw new MaintenanceConfigurationDependencyException("Policy 字段目录不可用: " + exception.getMessage());
        } catch (MaintenanceConfigurationDependencyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MaintenanceConfigurationDependencyException("Policy 字段目录调用失败");
        }
    }

    private ReferenceValidationEvidence validateReferences(String tenantId, ReferenceSets references) {
        try {
            return referencePort.validate(new ReferenceValidationRequest(
                    tenantId, references.rules(), references.permissions(), references.templates()));
        } catch (MaintenanceConfigurationDependencyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MaintenanceConfigurationDependencyException(
                    "保全配置引用校验调用失败: " + exception.getClass().getSimpleName());
        }
    }

    private void validateFields(MaintenanceItemDefinition definition,
            MaintenanceConfigurationValidationCriteria criteria, PolicyFieldCatalogEvidence catalog,
            List<ValidationIssue> issues) {
        Map<String, PolicyFieldDescriptorEvidence> catalogFields = new HashMap<>();
        catalog.fields().forEach(field -> catalogFields.put(field.fieldCode(), field));
        for (MaintenanceFieldRule rule : definition.fieldRules()) {
            PolicyFieldDescriptorEvidence field = catalogFields.get(rule.fieldCode());
            if (field == null) {
                issues.add(issue("FIELD_NOT_FOUND", rule.fieldCode(), "Policy 字段目录不存在该字段"));
                continue;
            }
            if (rule.visible() && !field.capability().readable()) {
                issues.add(issue("FIELD_NOT_READABLE", rule.fieldCode(), "配置为可见但字段不可读取"));
            }
            if (rule.editable() && !field.capability().proposable()) {
                issues.add(issue("FIELD_NOT_PROPOSABLE", rule.fieldCode(), "配置为可编辑但字段不可提交变更"));
            }
            if (rule.allowClear() && !field.capability().clearable()) {
                issues.add(issue("FIELD_NOT_CLEARABLE", rule.fieldCode(), "配置允许清空但字段不支持清空"));
            }
            if (rule.expectedValueType() == null) {
                issues.add(issue("FIELD_TYPE_EXPECTATION_REQUIRED", rule.fieldCode(),
                        "配置必须声明期望字段类型"));
            } else if (rule.expectedValueType() != field.valueType()) {
                issues.add(issue("FIELD_TYPE_MISMATCH", rule.fieldCode(),
                        "期望类型 " + rule.expectedValueType() + " 与目录类型 " + field.valueType() + " 不一致"));
            }
            if (field.deprecatedAt() != null && !criteria.businessDate().isBefore(field.deprecatedAt())) {
                issues.add(issue("FIELD_DEPRECATED", rule.fieldCode(), "字段在业务日期已停用"));
            }
            if (field.sensitivity().requiresMasking()
                    && !definition.controls().accessRule().viewPermissionCodes()
                            .contains(SENSITIVE_FIELD_VIEW_PERMISSION)) {
                issues.add(issue("SENSITIVE_FIELD_PERMISSION_REQUIRED", rule.fieldCode(),
                        "敏感字段必须配置敏感信息查看权限"));
            }
        }
    }

    private ReferenceSets referencesOf(MaintenanceItemDefinition definition) {
        MaintenanceItemControls controls = definition.controls();
        Set<String> rules = new HashSet<>(controls.crossFieldRuleCodes());
        add(rules, controls.approvalPolicyCode());
        add(rules, controls.feeRule().formulaCode());
        add(rules, controls.feeRule().settlementGateRuleCode());
        definition.fieldRules().forEach(rule -> add(rules, rule.conditionRuleCode()));
        controls.materialRequirements().forEach(material -> add(rules, material.conditionRuleCode()));

        Set<String> permissions = new HashSet<>(controls.accessRule().operationPermissionCodes());
        permissions.addAll(controls.accessRule().viewPermissionCodes());

        Set<String> templates = new HashSet<>(controls.outputRule().notificationTemplateCodes());
        add(templates, controls.outputRule().voucherTemplateCode());
        add(templates, controls.outputRule().archiveTemplateCode());
        return new ReferenceSets(Set.copyOf(rules), Set.copyOf(permissions), Set.copyOf(templates));
    }

    private void addMissingReferences(String code, String field, Set<String> requested,
            Set<String> resolved, List<ValidationIssue> issues) {
        requested.stream().filter(reference -> !resolved.contains(reference)).sorted()
                .forEach(reference -> issues.add(issue(code, field, "引用不存在: " + reference)));
    }

    private void add(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }

    private ValidationIssue issue(String code, String field, String message) {
        return new ValidationIssue(code, field, message);
    }

    private record ReferenceSets(Set<String> rules, Set<String> permissions, Set<String> templates) {
    }
}
