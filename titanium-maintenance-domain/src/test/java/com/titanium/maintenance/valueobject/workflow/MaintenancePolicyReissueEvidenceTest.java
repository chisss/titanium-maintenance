package com.titanium.maintenance.valueobject.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;

/**
 * 保单补发请求回执（第五种生效回执）值对象测试（g04-09）。
 * <p>
 * 锁死三条契约：① 回执版本取<b>生效请求号</b>（补发不推进保单版本，与放款回执同规），保证与案件级请求证据同源
 * 可勾稽；② {@code contentHash()} <b>不含</b> {@code requestedAt}——投递确认丢失后原载荷重试会取到新时刻，纳入
 * 摘要即幂等失效；③ 补发与其余四个出口（保单回执/投资转换/放款/还款）<b>互斥</b>，一笔生效只能由一个出口受理。
 * </p>
 * <p>
 * 互斥的对照出口取<b>保单回执</b>（构造要素最少且其不变量最严），足以证明「新增出口受同一条互斥不变量约束」；
 * 其余三个出口的互斥已由各自出口的既有用例覆盖。
 * </p>
 */
class MaintenancePolicyReissueEvidenceTest {

    private static final String        REQUEST_ID     = "effect-request-1";
    private static final String        POLICY_ID      = "policy-1";
    private static final String        REISSUE_ID     = "RISS-1";
    private static final String        ITEM_CODE      = "POLICY_REISSUE";
    private static final LocalDateTime NOW            = LocalDateTime.parse("2026-09-29T10:00:00");

    @Test
    @DisplayName("回执版本取生效请求号：补发不推进保单版本，与案件级请求证据同源可勾稽")
    void shouldExposeRequestIdAsEvidenceVersion() {
        MaintenancePolicyReissueEvidence evidence = reissueEvidence(REQUEST_ID, NOW);

        assertEquals(REQUEST_ID, evidence.evidenceVersion(),
                "补发不改保单版本，无从取保单版本作版本标识——必须回落到生效请求号");
    }

    @Test
    @DisplayName("🔴 contentHash 不含 requestedAt：仅出站时刻不同即同指纹，交付侧字段变更即异指纹")
    void shouldKeepContentHashIndependentOfRequestedAt() {
        MaintenancePolicyReissueEvidence first = reissueEvidence(REQUEST_ID, NOW);

        assertEquals(first.contentHash(), reissueEvidence(REQUEST_ID, NOW.plusHours(3)).contentHash(),
                "requestedAt 参与指纹将使同一笔补发重试时得到不同指纹——幂等失效");

        // 对照（证明上面的相等不是「指纹恒为常量」）
        assertNotEquals(first.contentHash(),
                new MaintenancePolicyReissueEvidence(
                        REQUEST_ID, "policy-2", REISSUE_ID, NOW).contentHash(),
                "补发指向的保单不同必须异指纹");
        assertNotEquals(first.contentHash(),
                new MaintenancePolicyReissueEvidence(
                        REQUEST_ID, POLICY_ID, "RISS-2", NOW).contentHash(),
                "交付侧补发请求号不同必须异指纹");
    }

    @Test
    @DisplayName("必填要素缺失：逐个拒绝构造，避免落一条无从勾稽的回执")
    void shouldRejectMissingMandatoryFields() {
        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenancePolicyReissueEvidence(" ", POLICY_ID, REISSUE_ID, NOW),
                "生效请求号为空须拒绝");
        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenancePolicyReissueEvidence(REQUEST_ID, null, REISSUE_ID, NOW),
                "保单ID为空须拒绝");
        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenancePolicyReissueEvidence(REQUEST_ID, POLICY_ID, "  ", NOW),
                "补发请求号为空须拒绝");
        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenancePolicyReissueEvidence(REQUEST_ID, POLICY_ID, REISSUE_ID, null),
                "回执时间为空须拒绝");
    }

    @Test
    @DisplayName("补发出口收口：回执记入后案件为已生效，其余四个出口保持未受理")
    void shouldRegisterPolicyReissueAsEffectExit() {
        MaintenanceEffectEvidence requested = MaintenanceEffectEvidence.requested(request());

        assertFalse(requested.isApplied(), "对照组：仅冻结请求（尚无任何出口回执）不得判为已生效");
        assertNull(requested.policyReissue(), "对照组：未回执时补发出口为空");

        MaintenanceEffectEvidence reissued = requested.reissued(reissueEvidence(REQUEST_ID, NOW));

        assertTrue(reissued.isApplied(), "补发回执记入后案件即已生效");
        assertEquals(REISSUE_ID, reissued.policyReissue().reissueRequestId(), "补发出口承载交付侧请求号");
        assertNull(reissued.application(), "补发案件不产生保单回执");
        assertNull(reissued.investmentSwitch());
        assertNull(reissued.loanDisbursement());
        assertNull(reissued.loanRepayment());
        assertEquals(requested.request(), reissued.request(), "回执不得篡改已冻结的生效请求");
    }

    @Test
    @DisplayName("补发回执的请求号必须与冻结请求一致：跨请求回执须拒绝")
    void shouldRejectPolicyReissueReceiptWithMismatchedRequestId() {
        assertThrows(MaintenanceValidationException.class,
                () -> MaintenanceEffectEvidence.requested(request())
                        .reissued(reissueEvidence("effect-request-2", NOW)),
                "回执与请求不同源时无从判定它属于哪一次生效——必须拒绝");
    }

    @Test
    @DisplayName("🔴 五出口互斥：补发与保单回执不得同时受理同一笔生效")
    void shouldKeepPolicyReissueExclusiveWithPolicyApplicationExit() {
        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenanceEffectEvidence(
                        request(), applicationEvidence(), null, null, null,
                        reissueEvidence(REQUEST_ID, NOW)),
                "一笔生效只能由一个出口受理：既产生保单回执又产出补发请求事实，说明分流判据失效");
    }

    // ==================== 夹具 ====================

    private static MaintenancePolicyReissueEvidence reissueEvidence(
            String requestId,
            LocalDateTime requestedAt) {
        return new MaintenancePolicyReissueEvidence(requestId, POLICY_ID, REISSUE_ID, requestedAt);
    }

    private static MaintenanceEffectRequestEvidence request() {
        return new MaintenanceEffectRequestEvidence(
                REQUEST_ID, "a".repeat(64), 7, EffectiveTimeType.IMMEDIATE,
                NOW.plusMinutes(1), "b".repeat(64), NOW);
    }

    /** 保单回执：与冻结请求同请求号同期望版本，实际版本递增、快照版本与实际一致、字段型回执含实际字段。 */
    private static MaintenancePolicyApplicationEvidence applicationEvidence() {
        return new MaintenancePolicyApplicationEvidence(
                REQUEST_ID, "ED-1", 7, 8, "c".repeat(64),
                new MaintenanceSnapshotReference(
                        "axon-event://policy/policy-1/maintenance/workflow-case-1",
                        "d".repeat(64), 8, OffsetDateTime.parse("2026-09-29T10:02:00+08:00")),
                List.of(new MaintenanceAppliedFieldEvidence(
                        ITEM_CODE, POLICY_ID, "policy.holder.mobile",
                        PolicyFieldDataType.TEXT, "13900000000")),
                NOW.plusMinutes(2));
    }
}
