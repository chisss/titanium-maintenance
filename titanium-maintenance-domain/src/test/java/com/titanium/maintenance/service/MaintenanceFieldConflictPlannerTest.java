package com.titanium.maintenance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

class MaintenanceFieldConflictPlannerTest {

    private static final String FIELD_CODE = "policy.holder.mobile";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-26T10:00:00+08:00");

    @Test
    void shouldDetectConflictAndRebuildProposedSnapshotFromLatestPolicy() {
        MaintenanceItemInstance item = item();
        MaintenanceFieldConflictPlan plan = new MaintenanceFieldConflictPlanner().refresh(
                MaintenanceId.of("case-1"), "tenant-1", snapshot(8, "13700000000"), List.of(item), NOW);

        assertEquals(1, plan.conflictCount());
        assertTrue(plan.allChanges().getFirst().hasUnresolvedConflict());
        assertEquals("13700000000", plan.allChanges().getFirst().currentValue().canonicalValue());
        assertEquals("13900000000", plan.proposedFieldValues().get(FIELD_CODE).canonicalValue());
        assertEquals(8, plan.proposedSnapshot().policyVersion());
    }

    @Test
    void shouldUseCurrentAndRemoveRemainingConflict() {
        MaintenanceFieldConflictPlanner planner = new MaintenanceFieldConflictPlanner();
        MaintenanceFieldConflictPlan refreshed = planner.refresh(
                MaintenanceId.of("case-1"), "tenant-1", snapshot(8, "13700000000"), List.of(item()), NOW);
        MaintenanceItemInstance refreshedItem = item().withFieldChanges(
                refreshed.changesByItem().get("POLICY_INFO_CHANGE"));

        MaintenanceFieldConflictPlan resolved = planner.resolve(
                MaintenanceId.of("case-1"), "tenant-1", 8, "policy-1", List.of(refreshedItem),
                refreshed.proposedFieldValues(), "POLICY_INFO_CHANGE", "policy-1", FIELD_CODE,
                MaintenanceFieldConflictResolutionAction.USE_CURRENT, null, NOW.plusMinutes(1));

        assertEquals(0, resolved.conflictCount());
        assertEquals("13700000000", resolved.proposedFieldValues().get(FIELD_CODE).canonicalValue());
    }

    private MaintenanceItemInstance item() {
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                "POLICY_INFO_CHANGE", "1.0.0", "保单基本信息变更",
                MaintenanceItemCategory.BASIC_INFORMATION, Set.of(MaintenanceChannel.MANUAL),
                List.of(MaintenanceFieldRule.editable(FIELD_CODE, true, true, PolicyFieldValueType.TEXT)),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.required(2, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), false);
        MaintenanceFieldChange change = MaintenanceFieldChange.propose(
                "POLICY_INFO_CHANGE", "policy-1", FIELD_CODE,
                MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"));
        return MaintenanceItemInstance.from(definition, LocalDateTime.parse("2026-08-25T10:00:00"))
                .withFieldChanges(List.of(change));
    }

    private PolicyMaintenanceSnapshot snapshot(long version, String mobile) {
        MaintenanceSnapshotReference reference = new MaintenanceSnapshotReference(
                "policy://policy-1/version/" + version, "a".repeat(64), version, NOW);
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P001", CustomerId.of("customer-1"),
                "product-1", "product-v1", "plan-v1", PolicyStatus.EFFECTIVE, version,
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"), reference,
                Map.of(FIELD_CODE, MaintenanceFieldValue.text(mobile)));
    }
}
