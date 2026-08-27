package com.titanium.maintenance.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;

/** 基于 Policy 最新快照刷新字段冲突并重建完整拟快照。 */
public final class MaintenanceFieldConflictPlanner {

    public MaintenanceFieldConflictPlan refresh(
            MaintenanceId maintenanceId,
            String tenantId,
            PolicyMaintenanceSnapshot currentSnapshot,
            List<MaintenanceItemInstance> items,
            OffsetDateTime refreshedAt) {
        requireContext(maintenanceId, tenantId, currentSnapshot, items, refreshedAt);
        Map<String, List<MaintenanceFieldChange>> changesByItem = new LinkedHashMap<>();
        for (MaintenanceItemInstance item : items) {
            List<MaintenanceFieldChange> changes = item.fieldChanges().stream()
                    .map(change -> change.refreshCurrent(snapshotValue(currentSnapshot, change)))
                    .toList();
            changesByItem.put(item.itemCode(), changes);
        }
        return plan(maintenanceId, tenantId, currentSnapshot.policyVersion(), currentSnapshot.fieldValues(),
                currentSnapshot.policyId().id(), changesByItem, refreshedAt);
    }

    public MaintenanceFieldConflictPlan resolve(
            MaintenanceId maintenanceId,
            String tenantId,
            long currentPolicyVersion,
            String policyId,
            List<MaintenanceItemInstance> items,
            Map<String, MaintenanceFieldValue> currentProposedValues,
            String itemCode,
            String objectId,
            String fieldCode,
            MaintenanceFieldConflictResolutionAction action,
            MaintenanceFieldValue reenteredValue,
            OffsetDateTime resolvedAt) {
        if (maintenanceId == null || !hasText(tenantId) || currentPolicyVersion < 0 || !hasText(policyId)
                || items == null || items.isEmpty() || currentProposedValues == null || action == null
                || resolvedAt == null) {
            throw validation("context", "字段冲突解决上下文不完整");
        }
        MaintenanceItemInstance targetItem = items.stream()
                .filter(item -> item.itemCode().equals(itemCode))
                .findFirst()
                .orElseThrow(() -> validation("itemCode", "案件中不存在保全项: " + itemCode));
        MaintenanceFieldChange before = targetItem.fieldChanges().stream()
                .filter(change -> change.objectId().equals(objectId) && change.fieldCode().equals(fieldCode))
                .findFirst()
                .orElseThrow(() -> validation("fieldCode", "案件中不存在指定字段冲突"));
        MaintenanceFieldChange after = resolve(before, action, reenteredValue);

        Map<String, List<MaintenanceFieldChange>> changesByItem = new LinkedHashMap<>();
        for (MaintenanceItemInstance item : items) {
            List<MaintenanceFieldChange> changes = item.itemCode().equals(itemCode)
                    ? item.fieldChanges().stream().map(change -> change.equals(before) ? after : change).toList()
                    : item.fieldChanges();
            changesByItem.put(item.itemCode(), changes);
        }
        return plan(maintenanceId, tenantId, currentPolicyVersion, currentProposedValues,
                policyId, changesByItem, resolvedAt);
    }

    /** 撤销项目后从各字段 current 值重建拟快照，防止被撤销提案残留。 */
    public MaintenanceFieldConflictPlan withdraw(
            MaintenanceId maintenanceId,
            String tenantId,
            long currentPolicyVersion,
            String policyId,
            Map<String, MaintenanceFieldValue> currentProposedValues,
            List<MaintenanceItemInstance> items,
            String withdrawnItemCode,
            OffsetDateTime withdrawnAt) {
        if (maintenanceId == null || !hasText(tenantId) || currentPolicyVersion < 0 || !hasText(policyId)
                || currentProposedValues == null || items == null || items.isEmpty()
                || !hasText(withdrawnItemCode) || withdrawnAt == null) {
            throw validation("context", "项目撤销快照重建上下文不完整");
        }
        if (items.stream().noneMatch(item -> item.itemCode().equals(withdrawnItemCode))) {
            throw validation("withdrawnItemCode", "案件中不存在待撤销项目: " + withdrawnItemCode);
        }
        TreeMap<String, MaintenanceFieldValue> currentValues = new TreeMap<>(currentProposedValues);
        items.stream().flatMap(item -> item.fieldChanges().stream()).forEach(change ->
                currentValues.put(snapshotKey(policyId, change), change.currentValue()));
        Map<String, List<MaintenanceFieldChange>> changesByItem = new LinkedHashMap<>();
        for (MaintenanceItemInstance item : items) {
            changesByItem.put(item.itemCode(), item.itemCode().equals(withdrawnItemCode)
                    ? List.of() : item.fieldChanges());
        }
        return plan(maintenanceId, tenantId, currentPolicyVersion, currentValues,
                policyId, changesByItem, withdrawnAt);
    }

    private MaintenanceFieldChange resolve(
            MaintenanceFieldChange before,
            MaintenanceFieldConflictResolutionAction action,
            MaintenanceFieldValue reenteredValue) {
        return switch (action) {
            case USE_CURRENT -> before.resolveUsingCurrent();
            case USE_PROPOSED -> before.resolveUsingProposed(action.getCode());
            case REENTER -> before.resolveUsingReentered(reenteredValue);
        };
    }

    private MaintenanceFieldConflictPlan plan(
            MaintenanceId maintenanceId,
            String tenantId,
            long policyVersion,
            Map<String, MaintenanceFieldValue> currentValues,
            String policyId,
            Map<String, List<MaintenanceFieldChange>> changesByItem,
            OffsetDateTime plannedAt) {
        TreeMap<String, MaintenanceFieldValue> proposedValues = new TreeMap<>(currentValues);
        changesByItem.values().stream().flatMap(List::stream).forEach(change ->
                proposedValues.put(snapshotKey(policyId, change), change.proposedValue()));
        String contentHash = hash(tenantId, maintenanceId, policyVersion, proposedValues);
        MaintenanceSnapshotReference reference = new MaintenanceSnapshotReference(
                "axon-event://maintenance/" + tenantId + "/" + maintenanceId.id()
                        + "/proposed?hash=" + contentHash,
                contentHash, policyVersion, plannedAt);
        int conflictCount = Math.toIntExact(changesByItem.values().stream()
                .flatMap(List::stream)
                .filter(MaintenanceFieldChange::hasUnresolvedConflict)
                .count());
        return new MaintenanceFieldConflictPlan(changesByItem, proposedValues, reference, conflictCount);
    }

    private MaintenanceFieldValue snapshotValue(
            PolicyMaintenanceSnapshot snapshot,
            MaintenanceFieldChange change) {
        MaintenanceFieldValue value = snapshot.fieldValues().get(snapshotKey(snapshot.policyId().id(), change));
        return value == null ? MaintenanceFieldValue.nullValue(change.baseValue().dataType()) : value;
    }

    private String snapshotKey(String policyId, MaintenanceFieldChange change) {
        return change.objectId().equals(policyId) ? change.fieldCode() : change.key();
    }

    private void requireContext(
            MaintenanceId maintenanceId,
            String tenantId,
            PolicyMaintenanceSnapshot currentSnapshot,
            List<MaintenanceItemInstance> items,
            OffsetDateTime refreshedAt) {
        if (maintenanceId == null || !hasText(tenantId) || currentSnapshot == null
                || items == null || items.isEmpty() || refreshedAt == null) {
            throw validation("context", "字段冲突刷新上下文不完整");
        }
        if (!tenantId.equals(currentSnapshot.tenantId())) {
            throw validation("tenantId", "Policy 当前快照与案件租户不一致");
        }
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException("MaintenanceFieldConflictPlanner", field, message);
    }
}
