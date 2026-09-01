package com.titanium.maintenance.web.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationCriteria;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationResult;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.configuration.MaintenanceConfigurationAuditEntry;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenancePublicationEvidence;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.configuration.control.MaintenanceAccessRule;
import com.titanium.maintenance.configuration.control.MaintenanceChannelCapability;
import com.titanium.maintenance.configuration.control.MaintenanceFeeRule;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.configuration.control.MaintenanceMaterialRequirement;
import com.titanium.maintenance.configuration.control.MaintenanceOutputRule;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationAuditPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationAuditRecord;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;
import com.titanium.maintenance.web.dto.configuration.MaintenanceConfigurationDTO;
import com.titanium.maintenance.web.dto.configuration.MaintenanceConfigurationValidationDTO;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationAuditPageVO;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationAuditPageVO.ChangeVO;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationPageVO;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationPreviewVO;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationVO;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationValidationVO;

import lombok.RequiredArgsConstructor;

/** 保全项配置后台 DTO、领域对象和脱敏 VO 的结构化映射。 */
@Component
@RequiredArgsConstructor
public class MaintenanceConfigurationWebMapper {

    private final ObjectMapper objectMapper;

    public MaintenanceItemDefinition toDefinition(MaintenanceConfigurationDTO request) {
        MaintenanceConfigurationDTO.DefinitionDTO definition = request.definition();
        return new MaintenanceItemDefinition(
                definition.itemCode(), definition.version(), definition.name(), definition.category(),
                definition.channels(),
                definition.fieldRules().stream().map(this::toFieldRule).toList(),
                definition.steps().stream().map(this::toStep).toList(),
                definition.feeMode(), toEffectiveRule(definition.effectiveRule()),
                definition.incompatibleItemCodes(), definition.atomicOnly(),
                toControls(definition.controls()));
    }

    public MaintenanceConfigurationValidationCriteria toCriteria(
            MaintenanceConfigurationValidationDTO request) {
        return new MaintenanceConfigurationValidationCriteria(
                request.productType(), request.policyType(), request.businessDate());
    }

    public MaintenanceConfigurationVO toVO(
            StoredConfiguration stored, boolean sensitiveDetailsVisible) {
        return toVO(stored.configuration(), stored.rowVersion(), sensitiveDetailsVisible);
    }

    public MaintenanceConfigurationPageVO toPageVO(ConfigurationPage page) {
        List<MaintenanceConfigurationPageVO.ItemVO> items = page.items().stream()
                .map(this::toPageItem)
                .toList();
        return new MaintenanceConfigurationPageVO(
                items, page.total(), page.page(), page.size(), page.totalPages());
    }

    public MaintenanceConfigurationPreviewVO toPreviewVO(
            StoredConfiguration stored, boolean sensitiveDetailsVisible) {
        MaintenanceItemConfiguration configuration = stored.configuration();
        MaintenanceItemDefinition definition = configuration.getDefinition();
        List<MaintenanceConfigurationPreviewVO.FieldVO> fields = definition.fieldRules().stream()
                .map(rule -> new MaintenanceConfigurationPreviewVO.FieldVO(
                        rule.fieldCode(), rule.required(), rule.visible(), rule.editable(), rule.allowClear(),
                        sensitiveDetailsVisible ? rule.expectedValueType() : null,
                        sensitiveDetailsVisible ? rule.validationType() : null,
                        sensitiveDetailsVisible ? rule.validationMessage() : null,
                        !sensitiveDetailsVisible))
                .toList();
        List<MaintenanceConfigurationPreviewVO.StepVO> steps = definition.steps().stream()
                .map(step -> new MaintenanceConfigurationPreviewVO.StepVO(
                        step.sequence(), step.stepType(), step.mode(),
                        step.mode() == MaintenanceStepMode.CONDITIONAL))
                .toList();
        MaintenanceEffectiveRule effectiveRule = definition.effectiveRule();
        return new MaintenanceConfigurationPreviewVO(
                configuration.getConfigurationId(), definition.itemCode(), definition.version(),
                definition.name(), configuration.getStatus(), configuration.getValidFrom(),
                configuration.getValidTo(), definition.channels(), fields, steps, definition.feeMode(),
                new MaintenanceConfigurationPreviewVO.EffectiveRuleVO(
                        effectiveRule.allowedModes(), effectiveRule.defaultMode(),
                        effectiveRule.maxRetroactiveDays(), effectiveRule.maxFutureDays()),
                definition.atomicOnly(), false);
    }

    public MaintenanceConfigurationValidationVO toValidationVO(
            MaintenanceConfigurationValidationResult result) {
        return new MaintenanceConfigurationValidationVO(
                result.valid(),
                result.issues().stream()
                        .map(issue -> new MaintenanceConfigurationValidationVO.IssueVO(
                                issue.code(), issue.field(), issue.message()))
                        .toList(),
                result.catalogVersion(), result.catalogHash(), result.referenceEvidenceVersion(),
                result.validatedAt());
    }

    public MaintenanceConfigurationAuditPageVO toAuditPageVO(
            ConfigurationAuditPage page, boolean sensitiveDetailsVisible) {
        List<MaintenanceConfigurationAuditPageVO.ItemVO> items = page.items().stream()
                .map(record -> toAuditItem(record, sensitiveDetailsVisible))
                .toList();
        return new MaintenanceConfigurationAuditPageVO(
                items, page.total(), page.page(), page.size(), page.totalPages());
    }

    private MaintenanceFieldRule toFieldRule(MaintenanceConfigurationDTO.FieldRuleDTO rule) {
        return new MaintenanceFieldRule(
                rule.fieldCode(), rule.required(), rule.visible(), rule.editable(), rule.allowClear(),
                rule.conditionRuleCode(), rule.expectedValueType(), rule.validationType(),
                rule.validationPattern(), rule.validationMessage());
    }

    private MaintenanceStepDefinition toStep(MaintenanceConfigurationDTO.StepDTO step) {
        return new MaintenanceStepDefinition(
                step.sequence(), step.stepType(), step.mode(), step.conditionRuleCode());
    }

    private MaintenanceEffectiveRule toEffectiveRule(
            MaintenanceConfigurationDTO.EffectiveRuleDTO rule) {
        return new MaintenanceEffectiveRule(
                rule.allowedModes(), rule.defaultMode(),
                rule.maxRetroactiveDays(), rule.maxFutureDays());
    }

    private MaintenanceItemControls toControls(MaintenanceConfigurationDTO.ControlsDTO controls) {
        Set<MaintenanceChannelCapability> capabilities = controls.channelCapabilities().stream()
                .map(capability -> new MaintenanceChannelCapability(
                        capability.channel(), capability.autoApprovalAllowed()))
                .collect(Collectors.toUnmodifiableSet());
        List<MaintenanceMaterialRequirement> materials = controls.materialRequirements().stream()
                .map(requirement -> new MaintenanceMaterialRequirement(
                        requirement.materialCode(), requirement.required(), requirement.conditionRuleCode()))
                .toList();
        MaintenanceConfigurationDTO.FeeRuleDTO fee = controls.feeRule();
        MaintenanceConfigurationDTO.AccessRuleDTO access = controls.accessRule();
        MaintenanceConfigurationDTO.OutputRuleDTO output = controls.outputRule();
        return new MaintenanceItemControls(
                capabilities, materials, controls.crossFieldRuleCodes(), controls.approvalPolicyCode(),
                new MaintenanceFeeRule(
                        fee.formulaCode(), fee.settlementGateRuleCode(), fee.recalculationTiming()),
                new MaintenanceAccessRule(
                        access.operationPermissionCodes(), access.viewPermissionCodes()),
                new MaintenanceOutputRule(
                        output.voucherTemplateCode(), output.notificationTemplateCodes(),
                        output.archiveTemplateCode()));
    }

    private MaintenanceConfigurationVO toVO(MaintenanceItemConfiguration configuration, Long rowVersion,
            boolean sensitiveDetailsVisible) {
        return new MaintenanceConfigurationVO(
                configuration.getConfigurationId(), configuration.getRevisionOfConfigurationId(),
                configuration.getStatus(), configuration.getValidFrom(), configuration.getValidTo(),
                normalize(configuration.getContentHash()), rowVersion, sensitiveDetailsVisible,
                toDefinitionVO(configuration.getDefinition(), sensitiveDetailsVisible),
                toPublicationEvidenceVO(configuration.getPublicationEvidence()),
                configuration.getAuditTrail().stream().map(this::toLifecycleAuditVO).toList());
    }

    private MaintenanceConfigurationVO.DefinitionVO toDefinitionVO(
            MaintenanceItemDefinition definition, boolean sensitiveDetailsVisible) {
        return new MaintenanceConfigurationVO.DefinitionVO(
                definition.itemCode(), definition.version(), definition.name(), definition.category(),
                definition.channels(),
                definition.fieldRules().stream()
                        .map(rule -> toFieldRuleVO(rule, sensitiveDetailsVisible))
                        .toList(),
                definition.steps().stream().map(this::toStepVO).toList(),
                definition.feeMode(), toEffectiveRuleVO(definition.effectiveRule()),
                definition.incompatibleItemCodes(), definition.atomicOnly(),
                toControlsVO(definition.controls()));
    }

    private MaintenanceConfigurationVO.FieldRuleVO toFieldRuleVO(
            MaintenanceFieldRule rule, boolean sensitiveDetailsVisible) {
        return new MaintenanceConfigurationVO.FieldRuleVO(
                rule.fieldCode(), rule.required(), rule.visible(), rule.editable(), rule.allowClear(),
                sensitiveDetailsVisible ? rule.conditionRuleCode() : null,
                sensitiveDetailsVisible ? rule.expectedValueType() : null,
                sensitiveDetailsVisible ? rule.validationType() : null,
                sensitiveDetailsVisible ? rule.validationPattern() : null,
                sensitiveDetailsVisible ? rule.validationMessage() : null,
                !sensitiveDetailsVisible);
    }

    private MaintenanceConfigurationVO.StepVO toStepVO(MaintenanceStepDefinition step) {
        return new MaintenanceConfigurationVO.StepVO(
                step.sequence(), step.stepType(), step.mode(), step.conditionRuleCode());
    }

    private MaintenanceConfigurationVO.EffectiveRuleVO toEffectiveRuleVO(MaintenanceEffectiveRule rule) {
        return new MaintenanceConfigurationVO.EffectiveRuleVO(
                rule.allowedModes(), rule.defaultMode(),
                rule.maxRetroactiveDays(), rule.maxFutureDays());
    }

    private MaintenanceConfigurationVO.ControlsVO toControlsVO(MaintenanceItemControls controls) {
        return new MaintenanceConfigurationVO.ControlsVO(
                controls.channelCapabilities().stream()
                        .map(capability -> new MaintenanceConfigurationVO.ChannelCapabilityVO(
                                capability.channel(), capability.autoApprovalAllowed()))
                        .collect(Collectors.toUnmodifiableSet()),
                controls.materialRequirements().stream()
                        .map(requirement -> new MaintenanceConfigurationVO.MaterialRequirementVO(
                                requirement.materialCode(), requirement.required(),
                                requirement.conditionRuleCode()))
                        .toList(),
                controls.crossFieldRuleCodes(), controls.approvalPolicyCode(),
                new MaintenanceConfigurationVO.FeeRuleVO(
                        controls.feeRule().formulaCode(), controls.feeRule().settlementGateRuleCode(),
                        controls.feeRule().recalculationTiming()),
                new MaintenanceConfigurationVO.AccessRuleVO(
                        controls.accessRule().operationPermissionCodes(),
                        controls.accessRule().viewPermissionCodes()),
                new MaintenanceConfigurationVO.OutputRuleVO(
                        controls.outputRule().voucherTemplateCode(),
                        controls.outputRule().notificationTemplateCodes(),
                        controls.outputRule().archiveTemplateCode()));
    }

    private MaintenanceConfigurationVO.PublicationEvidenceVO toPublicationEvidenceVO(
            MaintenancePublicationEvidence evidence) {
        return evidence == null ? null : new MaintenanceConfigurationVO.PublicationEvidenceVO(
                evidence.catalogVersion(), evidence.catalogHash(), evidence.validatedAt());
    }

    private MaintenanceConfigurationVO.LifecycleAuditVO toLifecycleAuditVO(
            MaintenanceConfigurationAuditEntry entry) {
        return new MaintenanceConfigurationVO.LifecycleAuditVO(
                entry.action(), entry.operatorId(), entry.occurredAt(), entry.detail());
    }

    private MaintenanceConfigurationPageVO.ItemVO toPageItem(StoredConfiguration stored) {
        MaintenanceItemConfiguration configuration = stored.configuration();
        MaintenanceItemDefinition definition = configuration.getDefinition();
        return new MaintenanceConfigurationPageVO.ItemVO(
                configuration.getConfigurationId(), definition.itemCode(), definition.version(),
                definition.name(), definition.steps().size(), definition.feeMode(),
                configuration.getStatus(), configuration.getValidFrom(),
                configuration.getValidTo(), normalize(configuration.getContentHash()), stored.rowVersion(),
                configuration.getAuditTrail().getLast().occurredAt());
    }

    private MaintenanceConfigurationAuditPageVO.ItemVO toAuditItem(
            ConfigurationAuditRecord record, boolean sensitiveDetailsVisible) {
        MaintenanceConfigurationVO before = record.before() == null
                ? null : toVO(record.before(), null, sensitiveDetailsVisible);
        MaintenanceConfigurationVO after = toVO(record.after(), null, sensitiveDetailsVisible);
        return new MaintenanceConfigurationAuditPageVO.ItemVO(
                record.auditId(), record.sequence(), record.action(), record.operatorId(), record.detail(),
                before, after, differences(before, after), record.beforeHash(), record.afterHash(),
                record.sourceIp(), record.correlationId(), record.operationResult(),
                record.occurredAt(), record.recordedAt());
    }

    private List<ChangeVO> differences(
            MaintenanceConfigurationVO before, MaintenanceConfigurationVO after) {
        JsonNode beforeNode = snapshotNode(before);
        JsonNode afterNode = snapshotNode(after);
        List<ChangeVO> changes = new ArrayList<>();
        collectDifferences("", beforeNode, afterNode, changes);
        return List.copyOf(changes);
    }

    private JsonNode snapshotNode(MaintenanceConfigurationVO configuration) {
        if (configuration == null) {
            return JsonNodeFactory.instance.nullNode();
        }
        ObjectNode node = objectMapper.valueToTree(configuration);
        node.remove(List.of("rowVersion", "sensitiveDetailsVisible", "lifecycleAudits"));
        return node;
    }

    private void collectDifferences(String path, JsonNode before, JsonNode after, List<ChangeVO> changes) {
        if (before.equals(after)) {
            return;
        }
        if (before.isObject() && after.isObject()) {
            Set<String> names = new TreeSet<>();
            before.fieldNames().forEachRemaining(names::add);
            after.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                collectDifferences(path + "/" + escape(name), valueOrNull(before.get(name)),
                        valueOrNull(after.get(name)), changes);
            }
            return;
        }
        changes.add(new ChangeVO(path.isEmpty() ? "/" : path, before, after));
    }

    private JsonNode valueOrNull(JsonNode value) {
        return value == null ? JsonNodeFactory.instance.nullNode() : value;
    }

    private String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
