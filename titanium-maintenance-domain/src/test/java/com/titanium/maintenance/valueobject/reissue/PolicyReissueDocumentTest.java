package com.titanium.maintenance.valueobject.reissue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 保单补发请求事实值对象测试（g04-09）。
 * <p>
 * 锁死三条契约：① 补发请求号<b>稳定派生</b>（同租户同案件恒定），重试/重放/重发收敛到同一个号，对端据以去重
 * ——流水号在重试时会变，幂等即失效；② {@code requestHash()} <b>不含</b> {@code requestedAt}，否则同一案件的
 * 冻结请求与重算载荷永不相等、幂等闸口恒拒；③ 摘要采用长度前缀 canonical 串，字段边界不同即摘要不同，杜绝
 * 「相邻字段拼接后同形」的碰撞。
 * </p>
 */
class PolicyReissueDocumentTest {

    private static final String        MAINTENANCE_ID = "maintenance-1";
    private static final String        POLICY_ID      = "policy-1";
    private static final String        TYPE_CODE      = "POLICY_REISSUE";
    private static final String        TENANT_ID      = "tenant-1";
    private static final LocalDateTime NOW            = LocalDateTime.parse("2026-09-29T10:00:00");

    @Test
    @DisplayName("补发请求号稳定派生：同租户同案件恒定，异案/异租户异号，恒为 RISS 前缀 32 字符")
    void shouldDeriveStableReissueRequestId() {
        String first = PolicyReissueDocument.deriveReissueRequestId(TENANT_ID, MAINTENANCE_ID);

        assertEquals(first, PolicyReissueDocument.deriveReissueRequestId(TENANT_ID, MAINTENANCE_ID),
                "同输入必须同号——重试/重放收敛到同一请求号是对端去重的前提");
        assertNotEquals(first, PolicyReissueDocument.deriveReissueRequestId(TENANT_ID, "maintenance-2"),
                "不同案件必须异号");
        assertNotEquals(first, PolicyReissueDocument.deriveReissueRequestId("tenant-2", MAINTENANCE_ID),
                "不同租户必须异号，否则跨租户撞号");
        assertTrue(first.startsWith("RISS"), () -> "请求号须带业务前缀，实际: " + first);
        assertEquals(32, first.length(), () -> "请求号须为 32 字符（RISS + sha256 前 28 位），实际: " + first);

        // 缺参不抛（派生前置校验在别处），但仍产出可比较的定长号
        assertEquals(32, PolicyReissueDocument.deriveReissueRequestId(null, null).length(),
                "缺参时仍须产出定长号，不得抛异常");
    }

    @Test
    @DisplayName("🔴 requestHash 不含 requestedAt：仅出站时刻不同即同摘要，其余字段变更即异摘要")
    void shouldKeepRequestHashIndependentOfRequestedAt() {
        PolicyReissueDocument first = document(NOW);

        assertEquals(first.requestHash(), document(NOW.plusHours(3)).requestHash(),
                "requestedAt 参与摘要将使同一案件的冻结请求与重算载荷永不相等——幂等闸口恒拒");

        // 对照（证明上面的相等不是「摘要恒为常量」）：逐字段改动必须使摘要变化
        assertNotEquals(first.requestHash(),
                document(MAINTENANCE_ID, "policy-2", TYPE_CODE, "RISS-1", NOW).requestHash(),
                "保单不同必须异摘要");
        assertNotEquals(first.requestHash(),
                document(MAINTENANCE_ID, POLICY_ID, TYPE_CODE, "RISS-2", NOW).requestHash(),
                "交付侧请求号不同必须异摘要");
    }

    @Test
    @DisplayName("摘要带长度前缀：字段边界不同即异摘要（防相邻字段拼接同形碰撞）")
    void shouldSeparateFieldBoundariesInRequestHash() {
        PolicyReissueDocument left = document("A", "BC", TYPE_CODE, "RISS-1", NOW);
        PolicyReissueDocument right = document("AB", "C", TYPE_CODE, "RISS-1", NOW);

        assertNotEquals(left.requestHash(), right.requestHash(),
                "无长度前缀时 (A,BC) 与 (AB,C) 拼接同形、摘要相同——幂等比对会把两件不同事实当成同一件");
    }

    @Test
    @DisplayName("必填要素缺失：逐个拒绝构造，避免下游按空要素建档")
    void shouldRejectMissingMandatoryFields() {
        assertRejected(() -> new PolicyReissueDocument(
                " ", POLICY_ID, TYPE_CODE, "RISS-1", NOW, TENANT_ID), "案件号为空须拒绝");
        assertRejected(() -> new PolicyReissueDocument(
                MAINTENANCE_ID, null, TYPE_CODE, "RISS-1", NOW, TENANT_ID), "保单ID为空须拒绝");
        assertRejected(() -> new PolicyReissueDocument(
                MAINTENANCE_ID, POLICY_ID, "", "RISS-1", NOW, TENANT_ID), "保全类型为空须拒绝");
        assertRejected(() -> new PolicyReissueDocument(
                MAINTENANCE_ID, POLICY_ID, TYPE_CODE, "  ", NOW, TENANT_ID), "请求号为空须拒绝");
        assertRejected(() -> new PolicyReissueDocument(
                MAINTENANCE_ID, POLICY_ID, TYPE_CODE, "RISS-1", NOW, null), "租户ID为空须拒绝");
        assertRejected(() -> new PolicyReissueDocument(
                MAINTENANCE_ID, POLICY_ID, TYPE_CODE, "RISS-1", null, TENANT_ID), "请求时间为空须拒绝");
    }

    @Test
    @DisplayName("文本要素去除首尾空白：同一事实的不同书写不得派生两个请求号")
    void shouldTrimTextElements() {
        PolicyReissueDocument document = new PolicyReissueDocument(
                "  " + MAINTENANCE_ID + "  ", POLICY_ID, TYPE_CODE, "RISS-1", NOW, TENANT_ID);

        assertEquals(MAINTENANCE_ID, document.maintenanceId());
        assertEquals(PolicyReissueDocument.deriveReissueRequestId(TENANT_ID, MAINTENANCE_ID),
                PolicyReissueDocument.deriveReissueRequestId(TENANT_ID, document.maintenanceId()),
                "未归一化空白时，带空格的案件号会派生出另一个请求号，对端去重即失效");
    }

    /** 断言构造被拒绝且携带 71 段校验错误码（构造放在 lambda 内，参数求值阶段不得抛） */
    private static void assertRejected(Executable constructor, String message) {
        MaintenanceValidationException rejection = assertThrows(
                MaintenanceValidationException.class, constructor, message);
        assertEquals(MaintenanceErrorCode.MAINTENANCE_COMMAND_VALIDATION_FAILED, rejection.getErrorCode(),
                "值对象拒绝须携带 71 段校验错误码");
    }

    private static PolicyReissueDocument document(LocalDateTime requestedAt) {
        return document(MAINTENANCE_ID, POLICY_ID, TYPE_CODE, "RISS-1", requestedAt);
    }

    private static PolicyReissueDocument document(
            String maintenanceId,
            String policyId,
            String typeCode,
            String reissueRequestId,
            LocalDateTime requestedAt) {
        return new PolicyReissueDocument(
                maintenanceId, policyId, typeCode, reissueRequestId, requestedAt, TENANT_ID);
    }
}
