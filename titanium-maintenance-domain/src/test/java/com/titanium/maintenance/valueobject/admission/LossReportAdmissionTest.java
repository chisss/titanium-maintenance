package com.titanium.maintenance.valueobject.admission;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.config.MaintenanceEffectPayloadKind;
import com.titanium.maintenance.common.exception.MaintenanceServiceRestrictedException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.ApplicationRequest;
import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort.FieldChange;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.maintenance.valueobject.effect.MaintenanceAmountEffect;
import com.titanium.maintenance.valueobject.effect.MaintenanceServiceRestrictionEffect;
import com.titanium.maintenance.valueobject.item.MaintenanceItemCode;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionAction;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 挂失 / 解除挂失 / 补发三项保全的准入守护测试（g04-09，AC-10）。
 *
 * <p>
 * AC-10 的两条判据「受限标记为真」「反向受限态下退保/贷款被拒」在本域各有一处可机械判定的落点，本测试逐条
 * 覆盖，并额外锁死三处「静默失效会致命」的契约：
 * </p>
 * <ol>
 * <li><b>登记完整</b>——三项新保全类型在状态准入矩阵中已登记且取值保守（{@code {EFFECTIVE}}）；</li>
 * <li><b>受限准入</b>——受限态下减损类（退保/贷款）被拒、错误码与「被拒的是哪一项」均可判定；</li>
 * <li><b>解除挂失不得被挡</b>——这是<b>死锁守卫</b>：受限态若连解除挂失都拒绝，受限将永不消失；</li>
 * <li><b>载荷链完整</b>——枚举（受限种类/动作）→ 生效载荷 → 保单应用请求 三段口径一致。</li>
 * </ol>
 *
 * <p>
 * 🔴 <b>本测试只覆盖本域前置准入</b>：权威拒绝在保单域写侧（{@code Policy.requireNotServiceRestricted}），
 * 前置只求「明显受限的案件不进入流程」。故此处断言的是「前置会拒」，而非「案件一定办不成」。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/29
 */
class LossReportAdmissionTest {

    // ==================== ① 状态准入：三项新类型已登记且取值保守 ====================

    @Test
    @DisplayName("挂失/补发/解除挂失三项已登记状态准入，且仅在保单生效态可办理")
    void shouldRegisterRestrictionTypesInStatusAdmissionMatrix() {
        for (MaintenanceType type : List.of(
                MaintenanceType.LOSS_REPORT,
                MaintenanceType.POLICY_REISSUE,
                MaintenanceType.LOSS_REPORT_RELEASE)) {
            assertTrue(MaintenanceAdmissionMatrix.registeredTypes().contains(type),
                    () -> "新保全类型必须登记在状态准入矩阵中: " + type);
            assertEquals(Set.of(PolicyStatus.EFFECTIVE),
                    MaintenanceAdmissionMatrix.ruleOf(type).admittedStatuses(),
                    () -> "受限/单证类保全项取值应保守为 {EFFECTIVE}: " + type);
            assertTrue(MaintenanceAdmissionMatrix.permits(type, PolicyStatus.EFFECTIVE),
                    () -> "生效态必须放行: " + type);
            // 反面对照组：证明上面的 permits 不是恒真——同一类型在其它状态必须为假
            for (PolicyStatus status : List.of(
                    PolicyStatus.SUSPENDED, PolicyStatus.LAPSED, PolicyStatus.TERMINATED)) {
                assertFalse(MaintenanceAdmissionMatrix.permits(type, status),
                        () -> "非生效态不得办理: " + type + " @ " + status);
            }
        }
    }

    // ==================== ② 受限准入：减损类被拒 ====================

    @Test
    @DisplayName("受限态下退保被拒：错误码 71008000 且拒绝文案指明被拒保全项")
    void shouldRejectTerminationWhenServiceRestricted() {
        MaintenanceServiceRestrictedException rejection = assertThrows(
                MaintenanceServiceRestrictedException.class,
                () -> MaintenanceServiceRestrictionAdmission.require(
                        Boolean.TRUE, List.of(MaintenanceType.POLICY_TERMINATION)));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_SERVICE_RESTRICTED, rejection.getErrorCode(),
                "受限拒绝必须携带 71 段错误码 71008000");
        assertTrue(rejection.getMessage().contains(MaintenanceType.POLICY_TERMINATION.getCode()),
                () -> "拒绝文案必须指明被拒保全项，实际: " + rejection.getMessage());
    }

    @Test
    @DisplayName("受限态下保单贷款被拒：与退保同为减损类，同一闸口同样拒绝")
    void shouldRejectPolicyLoanWhenServiceRestricted() {
        MaintenanceServiceRestrictedException rejection = assertThrows(
                MaintenanceServiceRestrictedException.class,
                () -> MaintenanceServiceRestrictionAdmission.require(
                        Boolean.TRUE, List.of(MaintenanceType.POLICY_LOAN)));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_SERVICE_RESTRICTED, rejection.getErrorCode());
        assertTrue(rejection.getMessage().contains(MaintenanceType.POLICY_LOAN.getCode()),
                () -> "拒绝文案必须指明保单贷款，实际: " + rejection.getMessage());
    }

    @Test
    @DisplayName("混装案件逐个判定：被拒项排在集合第二位时仍被识别（防只查首元素）")
    void shouldRejectTheBlockedItemEvenWhenItIsNotFirst() {
        MaintenanceServiceRestrictedException rejection = assertThrows(
                MaintenanceServiceRestrictedException.class,
                () -> MaintenanceServiceRestrictionAdmission.require(
                        Boolean.TRUE,
                        List.of(MaintenanceType.LOSS_REPORT_RELEASE, MaintenanceType.POLICY_TERMINATION)));

        assertTrue(rejection.getMessage().contains(MaintenanceType.POLICY_TERMINATION.getCode()),
                "被拒项不在首位时也须被识别——只看首个元素的实现会静默放行");
    }

    @Test
    @DisplayName("🔴 死锁守卫：受限态下解除挂失与常规保全项必须放行")
    void shouldAdmitReleaseAndUnrelatedItemsWhenServiceRestricted() {
        assertDoesNotThrow(
                () -> MaintenanceServiceRestrictionAdmission.require(
                        Boolean.TRUE, List.of(MaintenanceType.LOSS_REPORT_RELEASE)),
                "受限期间必须可办理解除挂失——否则受限态无法解除（受限 → 不得办理任何保全 → 受限永存）");
        assertDoesNotThrow(
                () -> MaintenanceServiceRestrictionAdmission.require(
                        Boolean.TRUE,
                        List.of(MaintenanceType.POLICY_INFO_CHANGE, MaintenanceType.LOSS_REPORT,
                                MaintenanceType.POLICY_REISSUE)),
                "受限只挡减损类：字段变更、再次挂失、补发均不在禁止集合内");
    }

    @Test
    @DisplayName("未受限/未知/空集合一律放行（fail-safe：前置只收窄，权威在保单域）")
    void shouldNotBlockWhenNotRestricted() {
        assertDoesNotThrow(
                () -> MaintenanceServiceRestrictionAdmission.require(
                        Boolean.FALSE,
                        List.of(MaintenanceType.POLICY_TERMINATION, MaintenanceType.POLICY_LOAN)),
                "未受限时不得误伤减损类");
        assertDoesNotThrow(
                () -> MaintenanceServiceRestrictionAdmission.require(
                        null, List.of(MaintenanceType.POLICY_TERMINATION)),
                "受限维度未知（快照未回显）按未受限放行——把它当受限会把保单域的一次发布顺序问题放大成全量不可办理");
        assertDoesNotThrow(
                () -> MaintenanceServiceRestrictionAdmission.require(Boolean.TRUE, null),
                "类型集合缺失由状态准入矩阵拒绝，本闸口不越权判定");
        assertDoesNotThrow(
                () -> MaintenanceServiceRestrictionAdmission.require(Boolean.TRUE, List.of()),
                "空集合无被拒对象");
    }

    @Test
    @DisplayName("禁止集合是唯一权威：恰为退保/贷款两项，且 blocks() 与集合逐类一致")
    void shouldKeepBlockedTypesInSingleAuthority() {
        Set<MaintenanceType> blocked = MaintenanceServiceRestrictionAdmission.inadmissibleTypes();
        // 绝对值而非「包含两个元素」：扩面必须同批改本断言，逼出「扩面是否会让受限态无法解除」这一问
        assertEquals(Set.of(MaintenanceType.POLICY_TERMINATION, MaintenanceType.POLICY_LOAN), blocked,
                "受限禁止集合变更须同批复核（R-5 挂失业务边界未定界前只准这两项）");

        for (MaintenanceType type : MaintenanceType.values()) {
            assertEquals(blocked.contains(type), MaintenanceServiceRestrictionAdmission.blocks(type),
                    () -> "blocks() 必须与禁止集合逐类一致，防两处规则漂移: " + type);
        }
        assertFalse(MaintenanceServiceRestrictionAdmission.blocks(null), "类型缺失一律不在此处拒绝");
        assertFalse(blocked.contains(MaintenanceType.LOSS_REPORT_RELEASE),
                "🔴 解除挂失不得进入禁止集合——这是死锁守卫的机械版");
        assertFalse(blocked.contains(MaintenanceType.LOSS_REPORT),
                "挂失本身不是减损动作，受限期间补办挂失不应被本闸口拒绝");
    }

    // ==================== ③ 载荷链：枚举 → 生效载荷 ====================

    @Test
    @DisplayName("挂失与解除挂失映射到【同一受限种类】的施加/解除（对称性）")
    void shouldMapLossReportPairToSymmetricActions() {
        assertEquals(PolicyServiceRestrictionType.LOSS_REPORT,
                MaintenanceType.LOSS_REPORT.policyServiceRestrictionType(),
                "挂失施加的受限种类为 LOSS_REPORT");
        assertEquals(PolicyServiceRestrictionAction.APPLY,
                MaintenanceType.LOSS_REPORT.policyServiceRestrictionAction());
        assertEquals(PolicyServiceRestrictionAction.RELEASE,
                MaintenanceType.LOSS_REPORT_RELEASE.policyServiceRestrictionAction(),
                "解除挂失的动作必须是解除");
        // 🔴 对称性：解除的受限种类必须与施加的相同，否则受限永不被清除
        assertEquals(MaintenanceType.LOSS_REPORT.policyServiceRestrictionType(),
                MaintenanceType.LOSS_REPORT_RELEASE.policyServiceRestrictionType(),
                "施加与解除必须指向同一受限种类");

        assertEquals(PolicyServiceRestrictionAction.NONE,
                MaintenanceType.POLICY_REISSUE.policyServiceRestrictionAction(),
                "补发不改变受限维度");
        assertNull(MaintenanceType.POLICY_REISSUE.policyServiceRestrictionType(),
                "补发无受限种类——它是单证请求事实，不落未定界的冻结规则");
    }

    @Test
    @DisplayName("枚举调用契约：受限种类非空 ⟺ 受限动作改变受限（双向，防新增类型只填一处）")
    void shouldKeepRestrictionTypeAndActionConsistent() {
        for (MaintenanceType type : MaintenanceType.values()) {
            boolean changesRestriction = type.policyServiceRestrictionAction().changesRestriction();
            assertEquals(changesRestriction, type.policyServiceRestrictionType() != null,
                    () -> "受限种类与受限动作必须同时给出或同时缺省: " + type);
            if (!changesRestriction) {
                assertEquals(PolicyServiceRestrictionAction.NONE,
                        type.policyServiceRestrictionAction(),
                        () -> "非受限类一律显式取 NONE（全函数兜底，不得为 null）: " + type);
            }
        }
    }

    @Test
    @DisplayName("生效载荷由枚举推出且构造期拒绝空交易；解除与施加可区分")
    void shouldBuildRestrictionEffectFromEnumContract() {
        MaintenanceServiceRestrictionEffect apply = new MaintenanceServiceRestrictionEffect(
                MaintenanceType.LOSS_REPORT.policyServiceRestrictionType(),
                MaintenanceType.LOSS_REPORT.policyServiceRestrictionAction());
        assertTrue(apply.applying(), "挂失载荷为施加受限");

        MaintenanceServiceRestrictionEffect release = new MaintenanceServiceRestrictionEffect(
                MaintenanceType.LOSS_REPORT_RELEASE.policyServiceRestrictionType(),
                MaintenanceType.LOSS_REPORT_RELEASE.policyServiceRestrictionAction());
        assertFalse(release.applying(), "解除挂失载荷为解除受限");
        assertEquals(apply.type(), release.type(), "施加与解除作用于同一受限种类");

        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenanceServiceRestrictionEffect(null, PolicyServiceRestrictionAction.APPLY),
                "受限种类缺失即空交易，须拒绝");
        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenanceServiceRestrictionEffect(
                        PolicyServiceRestrictionType.LOSS_REPORT, PolicyServiceRestrictionAction.NONE),
                "NONE 与「这是一条受限事实」自相矛盾，须拒绝");
        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenanceServiceRestrictionEffect(PolicyServiceRestrictionType.LOSS_REPORT, null));
    }

    @Test
    @DisplayName("保单应用请求：受限载荷可单独提交，且与字段/金额/状态动作互斥")
    void shouldKeepRestrictionPayloadExclusiveInApplicationRequest() {
        assertNotNull(restrictionRequest(List.of(), null, PolicyMaintenanceAction.NONE),
                "仅携带受限载荷的请求是合法请求（挂失案件无字段、无金额、不改合同状态）");

        // 三种混装各一例：任一种被放行即「一次请求表达两件事」，保单域无从判定谁是本次变更的实质
        assertThrows(MaintenanceValidationException.class,
                () -> restrictionRequest(
                        List.of(new FieldChange(
                                "LOSS_REPORT", "policy-1", "policy.holder.mobile", "TEXT", "13900000000")),
                        null, PolicyMaintenanceAction.NONE),
                "受限事实不得与字段变更混装");
        assertThrows(MaintenanceValidationException.class,
                () -> restrictionRequest(List.of(),
                        new MaintenanceAmountEffect(
                                "ADDITIONAL_PAYMENT", new BigDecimal("1000.00"),
                                MaintenanceBalanceDirection.DEBIT, "CNY", "POSTING-1"),
                        PolicyMaintenanceAction.NONE),
                "受限事实不得与金额事实混装");
        assertThrows(MaintenanceValidationException.class,
                () -> restrictionRequest(List.of(), null, PolicyMaintenanceAction.TERMINATE),
                "受限事实不得与合同状态动作混装——挂失不改合同状态");
    }

    // ==================== ④ 快照侧：受限维度参与幂等基准 ====================

    @Test
    @DisplayName("快照受限标记：非空受限列表为真，空/缺省为假")
    void shouldExposeServiceRestrictedFromSnapshot() {
        assertTrue(snapshot(List.of(PolicyServiceRestrictionType.LOSS_REPORT)).serviceRestricted(),
                "存在受限种类即受限——AC-10『挂失生效后服务受限标记为真』在本域的落点");
        assertFalse(snapshot(List.of()).serviceRestricted(),
                "无受限种类即不受限");
        assertFalse(snapshot(null).serviceRestricted(),
                "受限维度缺省（历史事件无此字段）按不受限处理，不得抛异常");
    }

    @Test
    @DisplayName("受限维度纳入幂等基准：仅受限不同即判为不同基准，相同则同一基准")
    void shouldIncludeRestrictionInBusinessBaseline() {
        PolicyMaintenanceSnapshot restricted = snapshot(List.of(PolicyServiceRestrictionType.LOSS_REPORT));
        assertTrue(restricted.sameBusinessBaseline(snapshot(List.of(PolicyServiceRestrictionType.LOSS_REPORT))),
                "同一保单版本的重复读取（受限维度相同）必须判为同一基准，否则建案重试会重复建案");
        assertFalse(restricted.sameBusinessBaseline(snapshot(List.of())),
                "受限维度未纳入比对时，挂失前后会被误判为同一基准——受限标记的变更必须使基准不同");
    }

    // ==================== ⑤ 保全项目录：登记与载荷种类 ====================

    @Test
    @DisplayName("保全项目录：挂失对登记为服务受限型，补发登记为专用跨域出口")
    void shouldRegisterRestrictionItemCodes() {
        MaintenanceItemCode lossReport = MaintenanceItemCode.of(MaintenanceType.LOSS_REPORT.getCode());
        assertTrue(lossReport.serviceRestrictionEffect(), "挂失的生效载荷是受限标记");
        assertEquals(MaintenanceEffectPayloadKind.SERVICE_RESTRICTION, lossReport.effectPayloadKind());
        assertEquals(MaintenanceType.LOSS_REPORT, lossReport.legacyMaintenanceType());

        MaintenanceItemCode release = MaintenanceItemCode.of(MaintenanceType.LOSS_REPORT_RELEASE.getCode());
        assertTrue(release.serviceRestrictionEffect(), "解除挂失同属受限型（施加与解除同形）");
        assertEquals(MaintenanceType.LOSS_REPORT_RELEASE, release.legacyMaintenanceType());

        MaintenanceItemCode reissue = MaintenanceItemCode.of(MaintenanceType.POLICY_REISSUE.getCode());
        assertFalse(reissue.serviceRestrictionEffect(), "补发不是受限型——它的载荷是单证请求事实");
        assertEquals(MaintenanceEffectPayloadKind.DEDICATED_EXIT, reissue.effectPayloadKind());
        assertEquals(MaintenanceType.POLICY_REISSUE, reissue.legacyMaintenanceType());

        assertEquals(Set.of(MaintenanceType.LOSS_REPORT, MaintenanceType.LOSS_REPORT_RELEASE),
                MaintenanceItemCode.serviceRestrictionTypes(),
                "受限型目录恰为挂失一对（新增须同批复核本断言）");
        assertFalse(MaintenanceItemCode.amountTypes().contains(MaintenanceType.LOSS_REPORT),
                "挂失无收付差额，不得误登记为金额型");
        assertFalse(MaintenanceItemCode.dedicatedExitTypes().contains(MaintenanceType.LOSS_REPORT),
                "挂失出口在保单域，不是跨域专用出口");
    }

    // ==================== 夹具 ====================

    private static ApplicationRequest restrictionRequest(
            List<FieldChange> changes,
            MaintenanceAmountEffect amountEffect,
            PolicyMaintenanceAction stateAction) {
        return new ApplicationRequest(
                "tenant-1", "policy-1", "effect-request-restriction", "maintenance-1", 7L,
                null, "a".repeat(64), "IMMEDIATE",
                LocalDateTime.parse("2026-09-29T10:00:00"),
                "maintenance=maintenance-1;items=LOSS_REPORT;fields=;action=NONE;restriction=APPLY:LOSS_REPORT",
                changes,
                amountEffect,
                new MaintenanceServiceRestrictionEffect(
                        PolicyServiceRestrictionType.LOSS_REPORT, PolicyServiceRestrictionAction.APPLY),
                stateAction,
                null, null, null,
                "operator-1");
    }

    private static PolicyMaintenanceSnapshot snapshot(List<PolicyServiceRestrictionType> restrictions) {
        OffsetDateTime capturedAt = OffsetDateTime.of(2026, 9, 29, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P202609290001", CustomerId.of("customer-1"),
                "product-1", "product-v3", "plan-v2", PolicyStatus.EFFECTIVE, restrictions, 7L, capturedAt,
                capturedAt.plusMonths(3), capturedAt.plusYears(1),
                new MaintenanceSnapshotReference(
                        "policy/policy-1/versions/7", "a".repeat(64), 7L, capturedAt),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13800000000")));
    }
}
