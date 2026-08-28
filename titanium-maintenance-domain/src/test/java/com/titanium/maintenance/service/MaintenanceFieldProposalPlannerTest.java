package com.titanium.maintenance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceFieldValidationType;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldCatalogSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldDescriptorSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldProposal;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldProposalPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

class MaintenanceFieldProposalPlannerTest {

    private static final String NAME = "policy.beneficiary.name";
    private static final String TYPE = "policy.beneficiary.relationship";
    private static final String SHARE = "policy.beneficiary.share";
    private static final String EXISTING_ID = "1294b715e99c41ce9a24bcd37d0e3fdf";
    private static final String NEW_ID = "2905c826f00d4a579583318e59a8797a";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-28T12:00:00+08:00");

    @Test
    void shouldAllowPartialUpdateWhenRequiredCollectionValuesAlreadyExist() {
        MaintenanceFieldProposalPlan plan = planner().plan(
                MaintenanceId.of("case-1"), "tenant-1", snapshot(), snapshot(), List.of(item()),
                "BENEFICIARY_CHANGE",
                List.of(new MaintenanceFieldProposal(
                        EXISTING_ID, NAME, PolicyFieldDataType.TEXT, "王五")),
                catalog(), NOW);

        assertEquals(1, plan.changes().size());
        assertEquals("王五", plan.proposedFieldValues().get(EXISTING_ID + ":" + NAME).canonicalValue());
        assertEquals("DEATH", plan.proposedFieldValues().get(EXISTING_ID + ":" + TYPE).canonicalValue());
        assertEquals("100", plan.proposedFieldValues().get(EXISTING_ID + ":" + SHARE).canonicalValue());
    }

    @Test
    void shouldRejectNewCollectionObjectWithoutAllRequiredValues() {
        MaintenanceValidationException exception = assertThrows(
                MaintenanceValidationException.class,
                () -> planner().plan(
                        MaintenanceId.of("case-1"), "tenant-1", snapshot(), snapshot(), List.of(item()),
                        "BENEFICIARY_CHANGE",
                        List.of(new MaintenanceFieldProposal(
                                NEW_ID, NAME, PolicyFieldDataType.TEXT, "赵六")),
                        catalog(), NOW));

        assertEquals("命令 MaintenanceFieldProposalPlanner 字段 proposals 校验失败: 缺少必填字段提案: " + TYPE,
                exception.getMessage());
    }

    @Test
    void shouldRejectProposalThatViolatesConfiguredFormat() {
        MaintenanceItemInstance formattedItem = itemWithNameRule(new MaintenanceFieldRule(
                NAME, true, true, true, false, null, PolicyFieldValueType.TEXT,
                MaintenanceFieldValidationType.EMAIL, null, "受益人邮箱格式不正确"));

        MaintenanceValidationException exception = assertThrows(
                MaintenanceValidationException.class,
                () -> planner().plan(
                        MaintenanceId.of("case-1"), "tenant-1", snapshot(), snapshot(),
                        List.of(formattedItem), "BENEFICIARY_CHANGE",
                        List.of(new MaintenanceFieldProposal(
                                EXISTING_ID, NAME, PolicyFieldDataType.TEXT, "invalid-email")),
                        catalog(), NOW));

        assertEquals("命令 MaintenanceFieldRule 字段 " + NAME + " 校验失败: 受益人邮箱格式不正确",
                exception.getMessage());
    }

    private MaintenanceFieldProposalPlanner planner() {
        return new MaintenanceFieldProposalPlanner();
    }

    private MaintenanceItemInstance item() {
        return itemWithNameRule(
                MaintenanceFieldRule.editable(NAME, true, false, PolicyFieldValueType.TEXT));
    }

    private MaintenanceItemInstance itemWithNameRule(MaintenanceFieldRule nameRule) {
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                "BENEFICIARY_CHANGE", "1.0.0", "受益人变更",
                MaintenanceItemCategory.CONTRACT_PARTY, Set.of(MaintenanceChannel.API),
                List.of(
                        nameRule,
                        MaintenanceFieldRule.editable(TYPE, true, false, PolicyFieldValueType.ENUM),
                        MaintenanceFieldRule.editable(SHARE, true, false, PolicyFieldValueType.DECIMAL)),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.required(2, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), false);
        return MaintenanceItemInstance.from(definition, LocalDateTime.parse("2026-08-28T11:00:00"));
    }

    private PolicyMaintenanceSnapshot snapshot() {
        Map<String, MaintenanceFieldValue> values = Map.of(
                EXISTING_ID + ":" + NAME, MaintenanceFieldValue.text("李四"),
                EXISTING_ID + ":" + TYPE, MaintenanceFieldValue.enumValue("DEATH"),
                EXISTING_ID + ":" + SHARE, MaintenanceFieldValue.decimal(BigDecimal.valueOf(100)));
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P001", CustomerId.of("customer-1"),
                "product-1", "product-v1", "plan-v1", PolicyStatus.EFFECTIVE, 14,
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
                new MaintenanceSnapshotReference("policy://policy-1/version/14", "a".repeat(64), 14, NOW),
                values);
    }

    private MaintenanceFieldCatalogSnapshot catalog() {
        return new MaintenanceFieldCatalogSnapshot(
                "tenant-1", LocalDate.of(2026, 8, 1), "catalog-v1", "b".repeat(64), NOW,
                Map.of(
                        NAME, descriptor(NAME, PolicyFieldValueType.TEXT),
                        TYPE, descriptor(TYPE, PolicyFieldValueType.ENUM),
                        SHARE, descriptor(SHARE, PolicyFieldValueType.DECIMAL)));
    }

    private MaintenanceFieldDescriptorSnapshot descriptor(
            String fieldCode,
            PolicyFieldValueType valueType) {
        return new MaintenanceFieldDescriptorSnapshot(
                fieldCode, PolicyFieldObjectType.BENEFICIARY, valueType, fieldCode,
                true, "beneficiaryId", true, true, false, true, "BENEFICIARY_CHANGE",
                PolicyFieldSensitivityLevel.PUBLIC, PolicyFieldMaskingPolicy.NONE, null);
    }
}
