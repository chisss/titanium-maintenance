package com.titanium.maintenance.valueobject.endorsement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

/**
 * 保全批单要素值对象测试
 * <p>
 * 锁死四条契约：① 「变更后」取 Policy 回执的权威生效值，<b>而非</b>案件拟值——这是批单作为对外批改凭证的
 * 正确性底线（回执值可能经 Policy 侧归一化，与拟值不同）；② 回执未覆盖某字段时回落拟值，不臆造、不丢失
 * 明细；③ 建档要素（保全案件号 / 保单ID / 保全类型 / 租户ID / 生效时间）齐备，供 document 域据以归属业务
 * 单据与隔离存储；④ <b>批单号逐字透传</b>（g05-08）——它是 Policy 台账的权威凭证号，本域只搬运不重算，
 * 且四条非保单交易出口上可为空（业务事实，非数据缺失）。
 * </p>
 */
class EndorsementDocumentTest {

    private static final String        ITEM_CODE      = "POLICY_INFO_CHANGE";
    private static final String        OBJECT_ID      = "policy-1";
    private static final String        FIELD_CODE     = "policy.holder.mobile";
    private static final String        TENANT_ID      = "tenant-1";
    /** 批单号取一个与任何本地派生值都不相同的字面量：断言「逐字透传」而非「本域可复算」 */
    private static final String        ENDORSEMENT_NO = "END-2026-0001";
    private static final LocalDateTime NOW            = LocalDateTime.parse("2026-08-25T11:00:00");

    @Test
    @DisplayName("变更后取 Policy 回执权威生效值，而非案件拟值")
    void shouldPreferPolicyAppliedValueOverProposedValue() {
        EndorsementDocument document = EndorsementDocument.of("M-001", OBJECT_ID, ITEM_CODE,
                List.of(item("original", "changed")),
                Map.of(EndorsementDocument.fieldKey(ITEM_CODE, OBJECT_ID, FIELD_CODE), "13900000000"),
                ENDORSEMENT_NO, NOW, NOW.plusMinutes(2), TENANT_ID);

        EndorsementFieldChange change = document.items().getFirst().fieldChanges().getFirst();
        assertEquals("original", change.beforeValue(), "变更前取本案基准值");
        assertEquals("13900000000", change.afterValue(),
                "变更后须取 Policy 回执的权威生效值，而非案件拟值 changed");
    }

    @Test
    @DisplayName("回执未覆盖该字段：回落案件拟值，不丢失变更明细")
    void shouldFallBackToProposedValueWhenReceiptMissesField() {
        EndorsementDocument document = EndorsementDocument.of("M-001", OBJECT_ID, ITEM_CODE,
                List.of(item("original", "changed")), Map.of(), ENDORSEMENT_NO, NOW, NOW.plusMinutes(2), TENANT_ID);

        EndorsementFieldChange change = document.items().getFirst().fieldChanges().getFirst();
        assertEquals("changed", change.afterValue(), "回执缺失时随拟值呈现，不臆造也不丢失");
    }

    @Test
    @DisplayName("建档要素齐备：案件号/保单ID/类型/租户/生效时间随要素出站")
    void shouldCarryFilingElementsForDownstreamDocument() {
        LocalDateTime effectiveTime = NOW.plusMinutes(1);
        LocalDateTime completedAt = NOW.plusMinutes(2);

        EndorsementDocument document = EndorsementDocument.of("M-001", OBJECT_ID, ITEM_CODE,
                List.of(item("original", "changed")), Map.of(), ENDORSEMENT_NO, effectiveTime, completedAt, TENANT_ID);

        assertEquals("M-001", document.maintenanceId(), "保全案件号是下游建档的业务单据ID");
        assertEquals(OBJECT_ID, document.policyId());
        assertEquals(ITEM_CODE, document.maintenanceTypeCode());
        assertEquals(TENANT_ID, document.tenantId(), "租户ID供下游隔离存储");
        assertEquals(effectiveTime, document.effectiveTime());
        assertEquals(completedAt, document.completedAt());
        assertEquals(ITEM_CODE, document.items().getFirst().itemCode());
        assertEquals(FIELD_CODE, document.items().getFirst().fieldChanges().getFirst().fieldCode());
    }

    @Test
    @DisplayName("批单号逐字透传：Policy 台账的权威凭证号原样随要素出站")
    void shouldCarryEndorsementNoVerbatim() {
        EndorsementDocument document = EndorsementDocument.of("M-001", OBJECT_ID, ITEM_CODE,
                List.of(item("original", "changed")), Map.of(), ENDORSEMENT_NO, NOW, NOW, TENANT_ID);

        assertEquals(ENDORSEMENT_NO, document.endorsementNo(),
                "批单号须逐字透传 Policy 台账的权威值，本域不得重算或加前后缀");
    }

    @Test
    @DisplayName("批单号可为空：四条非保单交易出口本就不产生批单号，不得为凑齐而臆造")
    void shouldTolerateMissingEndorsementNo() {
        EndorsementDocument document = EndorsementDocument.of("M-001", OBJECT_ID, ITEM_CODE,
                List.of(item("original", "changed")), Map.of(), null, NOW, NOW, TENANT_ID);

        assertNull(document.endorsementNo(),
                "投资账户转换/贷款放款/贷款还款/补发四条出口无批单号，以 null 承载事实缺失");
    }

    @Test
    @DisplayName("生效时间可为空：批改未解算出具体生效时点时不阻断出单")
    void shouldTolerateMissingEffectiveTime() {
        EndorsementDocument document = EndorsementDocument.of("M-001", OBJECT_ID, ITEM_CODE,
                List.of(item("original", "changed")), Map.of(), ENDORSEMENT_NO, null, NOW, TENANT_ID);

        assertNull(document.effectiveTime(), "生效时间缺失以 null 承载，由正文渲染层以占位符呈现");
    }

    @Test
    @DisplayName("无保全项：要素为空列表，不抛异常（案件仍可出批单）")
    void shouldTolerateMissingItems() {
        EndorsementDocument document = EndorsementDocument.of("M-001", OBJECT_ID, ITEM_CODE,
                null, null, ENDORSEMENT_NO, NOW, NOW, TENANT_ID);

        assertTrue(document.items().isEmpty(), "无保全项时批单要素为空，不臆造明细");
    }

    @Test
    @DisplayName("必填要素缺失：拒绝构造，避免下游按空要素建档")
    void shouldRejectBlankMandatoryFields() {
        assertThrows(MaintenanceValidationException.class,
                () -> EndorsementDocument.of(" ", OBJECT_ID, ITEM_CODE, List.of(), Map.of(), ENDORSEMENT_NO, NOW, NOW,
                        TENANT_ID),
                "保全案件号为空须拒绝");
        assertThrows(MaintenanceValidationException.class,
                () -> EndorsementDocument.of("M-001", null, ITEM_CODE, List.of(), Map.of(), ENDORSEMENT_NO, NOW, NOW,
                        TENANT_ID),
                "保单ID为空须拒绝");
        assertThrows(MaintenanceValidationException.class,
                () -> EndorsementDocument.of("M-001", OBJECT_ID, ITEM_CODE, List.of(), Map.of(), ENDORSEMENT_NO, NOW,
                        NOW, "  "),
                "租户ID为空须拒绝");
    }

    @Test
    @DisplayName("字段索引键：保全项+业务对象+字段三者联合，避免跨项跨对象串值")
    void shouldBuildFieldKeyFromAllThreeParts() {
        assertEquals("POLICY_INFO_CHANGE:policy-1:policy.holder.mobile",
                EndorsementDocument.fieldKey(ITEM_CODE, OBJECT_ID, FIELD_CODE));
    }

    /** 构造一个携带单条字段变更的保全项实例 */
    private static MaintenanceItemInstance item(String baseValue, String proposedValue) {
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                ITEM_CODE, "1.0.0", ITEM_CODE, MaintenanceItemCategory.BASIC_INFORMATION,
                Set.of(MaintenanceChannel.MANUAL),
                List.of(MaintenanceFieldRule.editable(FIELD_CODE, false, false, PolicyFieldValueType.TEXT)),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), false);
        MaintenanceFieldChange change = MaintenanceFieldChange.propose(
                ITEM_CODE, OBJECT_ID, FIELD_CODE,
                MaintenanceFieldValue.text(baseValue), MaintenanceFieldValue.text(proposedValue));
        return MaintenanceItemInstance.from(definition, NOW).withFieldChanges(List.of(change));
    }
}
