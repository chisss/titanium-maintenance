package com.titanium.maintenance.valueobject.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenancePolicyStatusNotAdmittedException;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 状态准入矩阵守护测试（g04-07）。
 *
 * <p>
 * 三类判据，覆盖「登记完整」「取值正确」「未登记即拒绝」：
 * </p>
 * <ol>
 * <li><b>登记完整</b>——26 条逐类登记，计数取<b>绝对值</b>并与枚举实际值数双向断言；</li>
 * <li><b>取值正确</b>——状态类 4 类的准入集合与保单域状态机<b>双口径互证</b>（本测试独立抄录状态机迁移表，
 * 不从矩阵读取）；字段类 19 类按 g04-07 裁示统一取 {@code {EFFECTIVE}}；</li>
 * <li><b>未登记即拒绝</b>——矩阵的 fail-closed 契约（含 null 状态），并锁定既有 8 类的对外错误码不变。</li>
 * </ol>
 *
 * @author wei.sun
 * @since 2026/9/29
 */
class MaintenanceAdmissionMatrixTest {

    /**
     * 保单域状态机迁移表——<b>独立抄录</b>，不从矩阵推导。
     *
     * <p>
     * 出处：{@code titanium-policy/titanium-policy-domain/src/main/java/com/titanium/policy/valueobject/
     * policy/PolicyStatus.java:80-120}（{@code validateTransition}）。本表写的是「哪些源状态可迁移到目标状态」，
     * 即目标状态的<b>前驱集合</b>——保全项能否办理，取决于它要把保单推向的那个目标状态是否可由当前状态到达。
     * </p>
     *
     * <p>
     * 🔴 <b>码表映射</b>：写侧 {@code NOT_EFFECTIVE} ↔ 读模型 {@code PENDING_EFFECTIVE}，是两套码的唯一差异
     * （见 {@code docs/技术文档/policyStatus双码映射对照表.md}）。本表按<b>读模型</b>记录，因两条建案路径
     * 实际看到的皆是读模型状态。
     * </p>
     *
     * <p>
     * 🔴 <b>本表只覆盖保全项会推向的目标状态</b>：{@code CANCELLED}（仅 {@code PENDING_EFFECTIVE} 可达）无对应
     * 保全项；{@code EXPIRED}（仅 {@code EFFECTIVE} 可达）由定时任务触发、非保全项。二者不参与互证。
     * </p>
     */
    private static final Map<PolicyStatus, Set<PolicyStatus>> STATE_MACHINE_PREDECESSORS =
            stateMachinePredecessors();

    private static Map<PolicyStatus, Set<PolicyStatus>> stateMachinePredecessors() {
        Map<PolicyStatus, Set<PolicyStatus>> predecessors = new EnumMap<>(PolicyStatus.class);
        // → EFFECTIVE（生效）：NOT_EFFECTIVE / SUSPENDED / LAPSED 三源可达（PolicyStatus.java:88-91,109-115）
        predecessors.put(PolicyStatus.EFFECTIVE, EnumSet.of(
                PolicyStatus.PENDING_EFFECTIVE, PolicyStatus.SUSPENDED, PolicyStatus.LAPSED));
        // → SUSPENDED（暂停）：仅 EFFECTIVE（PolicyStatus.java:96-99）
        predecessors.put(PolicyStatus.SUSPENDED, EnumSet.of(PolicyStatus.EFFECTIVE));
        // → TERMINATED（终止）：EFFECTIVE / SUSPENDED / LAPSED（PolicyStatus.java:100-104,116-119）
        predecessors.put(PolicyStatus.TERMINATED, EnumSet.of(
                PolicyStatus.EFFECTIVE, PolicyStatus.SUSPENDED, PolicyStatus.LAPSED));
        return Map.copyOf(predecessors);
    }

    // ==================== ① 登记完整：计数取绝对值 ====================

    @Test
    @DisplayName("矩阵逐类登记：登记条数 == 常量下界 == 枚举实际值数（三向断言）")
    void shouldRegisterEveryMaintenanceType() {
        // 绝对值而非「当前值 + 1」：相对口径会随漏登记一起漂移，掩盖缺口
        assertEquals(26, MaintenanceAdmissionMatrix.REGISTERED_TYPE_COUNT,
                "g04-09 矩阵必须登记 26 类；改动本常量前须先核对 MaintenanceType 枚举值数");

        Set<MaintenanceType> registered = MaintenanceAdmissionMatrix.registeredTypes();
        assertEquals(MaintenanceType.values().length, registered.size(),
                "矩阵登记数必须等于 MaintenanceType 实际值数——枚举新增类型而未登记即失败");
        assertEquals(MaintenanceAdmissionMatrix.REGISTERED_TYPE_COUNT, registered.size(),
                "矩阵登记数必须等于常量下界");
    }

    @Test
    @DisplayName("逐类登记：26 类每种都有非空准入规则与拒绝码")
    void shouldGiveEveryTypeNonNullRule() {
        for (MaintenanceType type : MaintenanceType.values()) {
            MaintenanceAdmissionRule rule = MaintenanceAdmissionMatrix.ruleOf(type);
            assertNotNull(rule, () -> "保全类型未登记准入规则: " + type);
            assertFalse(rule.admittedStatuses().isEmpty(), () -> "准入状态集合为空: " + type);
            assertNotNull(rule.rejectionCode(), () -> "拒绝码为空: " + type);
        }
    }

    // ==================== ② 取值正确：与保单域状态机双口径互证 ====================

    @Test
    @DisplayName("中止类取值与保单域状态机互证：准入集合 == →SUSPENDED 的前驱集合")
    void shouldMatchStateMachineForSuspension() {
        assertEquals(
                STATE_MACHINE_PREDECESSORS.get(PolicyStatus.SUSPENDED),
                MaintenanceAdmissionMatrix.ruleOf(MaintenanceType.POLICY_SUSPENSION).admittedStatuses(),
                "中止的准入集合必须等于状态机中 →SUSPENDED 的前驱集合（两侧独立取值，不互相推导）");
    }

    @Test
    @DisplayName("终止类取值与保单域状态机互证：准入集合 == →TERMINATED 的前驱集合")
    void shouldMatchStateMachineForTermination() {
        assertEquals(
                STATE_MACHINE_PREDECESSORS.get(PolicyStatus.TERMINATED),
                MaintenanceAdmissionMatrix.ruleOf(MaintenanceType.POLICY_TERMINATION).admittedStatuses(),
                "终止的准入集合必须等于状态机中 →TERMINATED 的前驱集合");
    }

    @Test
    @DisplayName("复效/恢复取值：各为单元素、彼此不相交，且都 ⊂ 状态机 →EFFECTIVE 的前驱集合")
    void shouldNarrowStateMachineForResumptionAndReinstatement() {
        Set<PolicyStatus> resumption =
                MaintenanceAdmissionMatrix.ruleOf(MaintenanceType.POLICY_RESUMPTION).admittedStatuses();
        Set<PolicyStatus> reinstatement =
                MaintenanceAdmissionMatrix.ruleOf(MaintenanceType.POLICY_REINSTATEMENT).admittedStatuses();
        Set<PolicyStatus> effectivePredecessors = STATE_MACHINE_PREDECESSORS.get(PolicyStatus.EFFECTIVE);

        // 🔴 这两类**窄于**状态机许可：→EFFECTIVE 有三源（待生效/暂停/失效）可达，但业务意图已把语义钉死——
        //    恢复的前提是「暂停」、复效的前提是「失效」。故此处断言「包含于」而非「相等」：
        //    写成相等会把正确取值判红，写成不检查则允许取值漂出状态机许可（如复效取 EFFECTIVE）。
        assertTrue(effectivePredecessors.containsAll(resumption),
                () -> "恢复的准入集合必须落在 →EFFECTIVE 的前驱集合内: " + resumption);
        assertTrue(effectivePredecessors.containsAll(reinstatement),
                () -> "复效的准入集合必须落在 →EFFECTIVE 的前驱集合内: " + reinstatement);

        assertEquals(Set.of(PolicyStatus.SUSPENDED), resumption, "恢复仅「暂停」态可办理");
        assertEquals(Set.of(PolicyStatus.LAPSED), reinstatement, "复效仅「失效」态可办理");
        assertEquals(0, countIntersection(resumption, reinstatement),
                "恢复与复效的准入集合不得相交——相交意味着同一保单状态可同时满足两种语义相反的业务");
    }

    @Test
    @DisplayName("状态类 4 类恰为 policyMaintenanceAction() 非 NONE 的 4 类（登记面与枚举口径互证）")
    void shouldCoverExactlyTheStateActionTypes() {
        Set<MaintenanceType> stateActionTypes = EnumSet.noneOf(MaintenanceType.class);
        for (MaintenanceType type : MaintenanceType.values()) {
            if (type.policyMaintenanceAction() != PolicyMaintenanceAction.NONE) {
                stateActionTypes.add(type);
            }
        }

        assertEquals(Set.of(
                MaintenanceType.POLICY_SUSPENSION, MaintenanceType.POLICY_RESUMPTION,
                MaintenanceType.POLICY_REINSTATEMENT, MaintenanceType.POLICY_TERMINATION),
                stateActionTypes,
                "状态类保全项集合 = policyMaintenanceAction() 非 NONE 者；枚举口径变动须同批复核矩阵");
    }

    @Test
    @DisplayName("字段类 19 类准入集合统一取 {EFFECTIVE}（g04-07 裁示：保守取值）")
    void shouldAdmitFieldTypesOnlyWhenEffective() {
        Set<MaintenanceType> stateActionTypes = Set.of(
                MaintenanceType.POLICY_SUSPENSION, MaintenanceType.POLICY_RESUMPTION,
                MaintenanceType.POLICY_REINSTATEMENT, MaintenanceType.POLICY_TERMINATION);
        int fieldTypes = 0;
        for (MaintenanceType type : MaintenanceType.values()) {
            if (stateActionTypes.contains(type)) {
                continue;
            }
            fieldTypes++;
            assertEquals(Set.of(PolicyStatus.EFFECTIVE),
                    MaintenanceAdmissionMatrix.ruleOf(type).admittedStatuses(),
                    () -> "字段/单证/受限类保全项准入集合必须为 {EFFECTIVE}: " + type);
        }
        assertEquals(22, fieldTypes, "字段/单证/受限类应为 22 类（26 - 状态类 4），实际 " + fieldTypes);
    }

    // ==================== ③ 未登记即拒绝 + 对外契约不变 ====================

    @Test
    @DisplayName("未登记即拒绝：类型为 null / 状态为 null 一律抛异常，绝不放行")
    void shouldFailClosedOnUnregisteredOrNull() {
        assertFalse(MaintenanceAdmissionMatrix.permits(MaintenanceType.POLICY_INFO_CHANGE, null),
                "状态不可识别（null）必须拒绝——旧版折叠布尔 + default 放行会把它静默放行");
        assertFalse(MaintenanceAdmissionMatrix.permits(null, PolicyStatus.EFFECTIVE),
                "类型未登记必须拒绝");

        MaintenancePolicyStatusNotAdmittedException nullType = assertThrows(
                MaintenancePolicyStatusNotAdmittedException.class,
                () -> MaintenanceAdmissionMatrix.require(null, PolicyStatus.EFFECTIVE));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_POLICY_STATUS_NOT_ADMITTED, nullType.getErrorCode());

        MaintenancePolicyStatusNotAdmittedException nullStatus = assertThrows(
                MaintenancePolicyStatusNotAdmittedException.class,
                () -> MaintenanceAdmissionMatrix.require(MaintenanceType.POLICY_INFO_CHANGE, null));
        assertEquals(MaintenanceErrorCode.POLICY_NOT_ACTIVE, nullStatus.getErrorCode());
    }

    @Test
    @DisplayName("既有 8 类对外契约不变：7 个字段类用 POLICY_NOT_ACTIVE，复效用 POLICY_NOT_TERMINATED")
    void shouldKeepLegacyRejectionCodesUnchanged() {
        for (MaintenanceType type : List.of(
                MaintenanceType.POLICY_INFO_CHANGE, MaintenanceType.POLICY_PERIOD_CHANGE,
                MaintenanceType.COVERAGE_AMOUNT_CHANGE, MaintenanceType.INSURED_INFO_CHANGE,
                MaintenanceType.SUBJECT_CHANGE, MaintenanceType.SMOKING_STATUS_CHANGE,
                MaintenanceType.COVERAGE_CHANGE)) {
            assertEquals(MaintenanceErrorCode.POLICY_NOT_ACTIVE,
                    MaintenanceAdmissionMatrix.ruleOf(type).rejectionCode(),
                    () -> "既有字段类拒绝码必须保持 POLICY_NOT_ACTIVE: " + type);
        }
        assertEquals(MaintenanceErrorCode.POLICY_NOT_TERMINATED,
                MaintenanceAdmissionMatrix.ruleOf(MaintenanceType.POLICY_REINSTATEMENT).rejectionCode(),
                "既有复效拒绝码必须保持 POLICY_NOT_TERMINATED");

        // 反面对照组：g04-07 新纳入的 12 类使用新码——证明上面的『不变』不是因为全部都用同一个码
        assertEquals(MaintenanceErrorCode.MAINTENANCE_POLICY_STATUS_NOT_ADMITTED,
                MaintenanceAdmissionMatrix.ruleOf(MaintenanceType.POLICY_LOAN).rejectionCode(),
                "g04-07 新纳入类型应使用新错误码 71007013");
    }

    @Test
    @DisplayName("准入集合不可变：回报的集合被改动须抛异常（防规则被外部就地改写）")
    void shouldExposeImmutableAdmittedStatuses() {
        Set<PolicyStatus> admitted =
                MaintenanceAdmissionMatrix.ruleOf(MaintenanceType.POLICY_TERMINATION).admittedStatuses();
        assertThrows(UnsupportedOperationException.class, () -> admitted.add(PolicyStatus.EXPIRED));
    }

    private static int countIntersection(Set<PolicyStatus> left, Set<PolicyStatus> right) {
        return (int) left.stream().filter(right::contains).count();
    }
}
