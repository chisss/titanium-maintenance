package com.titanium.maintenance.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldCatalogSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldDescriptorSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldProposal;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldProposalPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;

/** 基于冻结配置、Policy 快照和字段目录生成完整字段差异及拟变更快照。 */
public final class MaintenanceFieldProposalPlanner {

    private static final int MAX_PROPOSALS = 100;

    /** 纯领域计算，不读取仓储或远程 Port。 */
    public MaintenanceFieldProposalPlan plan(
            MaintenanceId maintenanceId,
            String tenantId,
            PolicyMaintenanceSnapshot baseSnapshot,
            PolicyMaintenanceSnapshot currentSnapshot,
            List<MaintenanceItemInstance> items,
            String targetItemCode,
            List<MaintenanceFieldProposal> proposals,
            MaintenanceFieldCatalogSnapshot catalogSnapshot,
            OffsetDateTime plannedAt) {
        validateContext(maintenanceId, tenantId, baseSnapshot, currentSnapshot, catalogSnapshot, plannedAt);
        if (items == null || items.isEmpty()) {
            throw validation("items", "案件尚未冻结保全项");
        }
        if (proposals == null || proposals.isEmpty() || proposals.size() > MAX_PROPOSALS) {
            throw validation("proposals", "字段提案数量必须为 1 到 " + MAX_PROPOSALS);
        }
        MaintenanceItemInstance targetItem = items.stream()
                .filter(item -> item.itemCode().equals(targetItemCode))
                .findFirst()
                .orElseThrow(() -> validation("targetItemCode", "案件中不存在保全项: " + targetItemCode));

        Map<String, MaintenanceFieldRule> rules = new HashMap<>();
        targetItem.fieldRules().forEach(rule -> rules.put(rule.fieldCode(), rule));
        List<MaintenanceFieldChange> changes = new ArrayList<>();
        Map<String, Boolean> proposalKeys = new HashMap<>();
        for (MaintenanceFieldProposal proposal : proposals) {
            MaintenanceFieldRule rule = requireEditableRule(rules, proposal.fieldCode());
            MaintenanceFieldDescriptorSnapshot descriptor = catalogSnapshot.requireField(proposal.fieldCode());
            String objectId = resolveObjectId(baseSnapshot, proposal, descriptor);
            String changeKey = objectId + ":" + proposal.fieldCode();
            if (proposalKeys.putIfAbsent(changeKey, Boolean.TRUE) != null) {
                throw validation("proposals", "同一业务对象字段不能重复提交: " + changeKey);
            }
            validateField(rule, proposal, descriptor, catalogSnapshot.businessDate());
            MaintenanceFieldValue baseValue = snapshotValue(baseSnapshot, descriptor, objectId);
            MaintenanceFieldValue currentValue = snapshotValue(currentSnapshot, descriptor, objectId);
            MaintenanceFieldChange change = MaintenanceFieldChange.propose(
                            targetItemCode, objectId, proposal.fieldCode(), baseValue, proposal.value())
                    .refreshCurrent(currentValue);
            changes.add(change);
        }
        targetItem.withFieldChanges(changes);

        Map<String, MaintenanceFieldValue> proposedValues = mergeProposals(
                baseSnapshot, currentSnapshot, items, targetItemCode, changes);
        validateRequiredFields(targetItem, changes, proposedValues, catalogSnapshot);
        String contentHash = hash(tenantId, maintenanceId, currentSnapshot.policyVersion(), proposedValues);
        MaintenanceSnapshotReference reference = new MaintenanceSnapshotReference(
                "axon-event://maintenance/" + tenantId + "/" + maintenanceId.id()
                        + "/proposed?hash=" + contentHash,
                contentHash, currentSnapshot.policyVersion(), plannedAt);
        return new MaintenanceFieldProposalPlan(changes, proposedValues, reference);
    }

    private void validateContext(
            MaintenanceId maintenanceId,
            String tenantId,
            PolicyMaintenanceSnapshot baseSnapshot,
            PolicyMaintenanceSnapshot currentSnapshot,
            MaintenanceFieldCatalogSnapshot catalogSnapshot,
            OffsetDateTime plannedAt) {
        if (maintenanceId == null || baseSnapshot == null || currentSnapshot == null
                || catalogSnapshot == null || plannedAt == null) {
            throw validation("context", "字段提案上下文不完整");
        }
        if (!baseSnapshot.tenantId().equals(tenantId)
                || !currentSnapshot.tenantId().equals(tenantId)
                || !catalogSnapshot.tenantId().equals(tenantId)) {
            throw validation("tenantId", "字段提案租户上下文不一致");
        }
        if (!baseSnapshot.policyId().equals(currentSnapshot.policyId())) {
            throw validation("currentSnapshot", "当前 Policy 快照与案件保单不一致");
        }
        if (currentSnapshot.policyVersion() < baseSnapshot.policyVersion()) {
            throw validation("currentSnapshot", "当前 Policy 版本不能早于案件基准版本");
        }
        if (!catalogSnapshot.businessDate().equals(baseSnapshot.businessEffectiveAt().toLocalDate())) {
            throw validation("catalogSnapshot", "字段目录业务日期与案件基准不一致");
        }
    }

    private MaintenanceFieldRule requireEditableRule(
            Map<String, MaintenanceFieldRule> rules,
            String fieldCode) {
        MaintenanceFieldRule rule = rules.get(fieldCode);
        if (rule == null || !rule.editable()) {
            throw validation("proposals", "字段不在项目可编辑白名单: " + fieldCode);
        }
        return rule;
    }

    private String resolveObjectId(
            PolicyMaintenanceSnapshot baseSnapshot,
            MaintenanceFieldProposal proposal,
            MaintenanceFieldDescriptorSnapshot descriptor) {
        if (!descriptor.collection()) {
            if (proposal.objectId() != null) {
                throw validation("objectId", "非集合字段不能指定业务对象ID: " + proposal.fieldCode());
            }
            return baseSnapshot.policyId().id();
        }
        if (proposal.objectId() == null) {
            throw validation("objectId", "集合字段必须指定稳定业务对象ID: " + proposal.fieldCode());
        }
        if (proposal.objectId().equals(baseSnapshot.policyId().id())) {
            throw validation("objectId", "集合对象ID不能与保单ID相同");
        }
        return proposal.objectId();
    }

    private void validateField(
            MaintenanceFieldRule rule,
            MaintenanceFieldProposal proposal,
            MaintenanceFieldDescriptorSnapshot descriptor,
            LocalDate businessDate) {
        if (!descriptor.readable() || !descriptor.proposable() || !descriptor.activeAt(businessDate)) {
            throw validation("proposals", "Policy 字段当前不可读取或不可提案: " + proposal.fieldCode());
        }
        if (rule.expectedValueType() == null || rule.expectedValueType() != descriptor.valueType()) {
            throw validation("proposals", "项目配置与 Policy 字段目录类型不一致: " + proposal.fieldCode());
        }
        PolicyFieldDataType expectedType = PolicyFieldDataType.valueOf(descriptor.valueType().name());
        if (proposal.dataType() != expectedType) {
            throw validation("proposals", "提案值类型与 Policy 字段目录不一致: " + proposal.fieldCode());
        }
        if (proposal.value().isNull() && (!rule.allowClear() || !descriptor.clearable())) {
            throw validation("proposals", "字段不允许清空: " + proposal.fieldCode());
        }
        rule.validateValue(proposal.value());
    }

    private MaintenanceFieldValue snapshotValue(
            PolicyMaintenanceSnapshot snapshot,
            MaintenanceFieldDescriptorSnapshot descriptor,
            String objectId) {
        PolicyFieldDataType dataType = PolicyFieldDataType.valueOf(descriptor.valueType().name());
        String key = descriptor.collection()
                ? objectId + ":" + descriptor.fieldCode()
                : descriptor.fieldCode();
        MaintenanceFieldValue value = snapshot.fieldValues().get(key);
        if (value == null) {
            return MaintenanceFieldValue.nullValue(dataType);
        }
        if (value.dataType() != dataType) {
            throw validation("snapshot", "Policy 快照字段类型与目录不一致: " + descriptor.fieldCode());
        }
        return value;
    }

    private void validateRequiredFields(
            MaintenanceItemInstance item,
            List<MaintenanceFieldChange> changes,
            Map<String, MaintenanceFieldValue> proposedValues,
            MaintenanceFieldCatalogSnapshot catalogSnapshot) {
        Map<PolicyFieldObjectType, Set<String>> touchedCollectionObjects = new HashMap<>();
        for (MaintenanceFieldChange change : changes) {
            MaintenanceFieldDescriptorSnapshot descriptor = catalogSnapshot.requireField(change.fieldCode());
            if (descriptor.collection()) {
                touchedCollectionObjects
                        .computeIfAbsent(descriptor.objectType(), ignored -> new HashSet<>())
                        .add(change.objectId());
            }
        }
        for (MaintenanceFieldRule rule : item.fieldRules()) {
            if (!rule.required() || rule.conditionRuleCode() != null) {
                continue;
            }
            MaintenanceFieldDescriptorSnapshot descriptor = catalogSnapshot.requireField(rule.fieldCode());
            if (!descriptor.collection()) {
                requireProposedValue(proposedValues, rule.fieldCode(), rule.fieldCode());
                continue;
            }
            for (String objectId : touchedCollectionObjects.getOrDefault(descriptor.objectType(), Set.of())) {
                requireProposedValue(
                        proposedValues, objectId + ":" + rule.fieldCode(), rule.fieldCode());
            }
        }
    }

    private void requireProposedValue(
            Map<String, MaintenanceFieldValue> proposedValues,
            String snapshotKey,
            String fieldCode) {
        MaintenanceFieldValue value = proposedValues.get(snapshotKey);
        if (value == null || value.isNull()) {
            throw validation("proposals", "缺少必填字段提案: " + fieldCode);
        }
    }

    private Map<String, MaintenanceFieldValue> mergeProposals(
            PolicyMaintenanceSnapshot baseSnapshot,
            PolicyMaintenanceSnapshot currentSnapshot,
            List<MaintenanceItemInstance> items,
            String targetItemCode,
            List<MaintenanceFieldChange> targetChanges) {
        TreeMap<String, MaintenanceFieldValue> proposedValues = new TreeMap<>(currentSnapshot.fieldValues());
        Map<String, String> owners = new HashMap<>();
        for (MaintenanceItemInstance item : items) {
            List<MaintenanceFieldChange> itemChanges = item.itemCode().equals(targetItemCode)
                    ? targetChanges
                    : item.fieldChanges();
            for (MaintenanceFieldChange change : itemChanges) {
                String previousOwner = owners.putIfAbsent(change.key(), item.itemCode());
                if (previousOwner != null && !previousOwner.equals(item.itemCode())) {
                    throw validation("proposals", "多个保全项不能修改同一业务对象字段: " + change.key());
                }
                String snapshotKey = change.objectId().equals(baseSnapshot.policyId().id())
                        ? change.fieldCode()
                        : change.key();
                proposedValues.put(snapshotKey, change.proposedValue());
            }
        }
        return proposedValues;
    }

    private String hash(
            String tenantId,
            MaintenanceId maintenanceId,
            long policyVersion,
            Map<String, MaintenanceFieldValue> values) {
        StringBuilder canonical = new StringBuilder();
        appendCanonical(canonical, tenantId);
        appendCanonical(canonical, maintenanceId.id());
        appendCanonical(canonical, Long.toString(policyVersion));
        new TreeMap<>(values).forEach((fieldKey, value) -> {
            appendCanonical(canonical, fieldKey);
            appendCanonical(canonical, value.dataType().name());
            appendCanonical(canonical, value.canonicalValue());
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }

    private void appendCanonical(StringBuilder target, String value) {
        target.append(value == null ? -1 : value.length()).append(':');
        if (value != null) {
            target.append(value);
        }
    }

    private MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceFieldProposalPlanner", fieldName, message);
    }
}
