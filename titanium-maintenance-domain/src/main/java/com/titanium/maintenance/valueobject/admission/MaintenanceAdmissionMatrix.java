package com.titanium.maintenance.valueobject.admission;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.titanium.maintenance.common.exception.MaintenancePolicyStatusNotAdmittedException;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 保全项状态准入矩阵——「保全类型 × 保单状态」能否办理的<b>单一权威</b>。
 *
 * <p>
 * <b>为何需要本矩阵</b>：状态准入原先散落在两处且判据不同源——① 旧版建案入口的
 * {@code switch (maintenanceType)} 只显式覆盖 8/23 类，其余 15 类落 {@code default} <b>放行</b>
 * （含退保、贷款、减保、部分领取，以及本该对前驱状态有硬要求的 {@code POLICY_SUSPENSION} /
 * {@code POLICY_RESUMPTION} / {@code POLICY_TERMINATION}）；② 独立建案入口则完全依赖产品
 * {@code ProductMaintenanceOffering} 的 {@code allowedPolicyStatuses} 数据，<b>无领域兜底</b>，
 * 该数据源可配出与保单域状态机不相容的组合。收敛到本矩阵后，两条建案路径共享同一准入事实。
 * </p>
 *
 * <p>
 * 🔴 <b>未登记即拒绝（fail-closed）</b>：{@link MaintenanceType} 新增类型而未在本矩阵登记时，
 * {@link #require} 一律显式拒绝，<b>不再有 {@code default} 放行</b>。登记条数由测试以绝对下界
 * （{@link #REGISTERED_TYPE_COUNT}）机械断言，漏登记即构建失败。
 * </p>
 *
 * <p>
 * 🔴 <b>取值域是读模型词表</b> {@link PolicyStatus}（7 值）——两条建案路径实际看到的保单状态皆出自
 * 保单域读模型（{@code t_policy_view.policy_status}），非写侧 {@code PolicyStatusCode}。两套码的唯一
 * 差异是「未生效」：写侧 {@code NOT_EFFECTIVE} ↔ 读模型 {@code PENDING_EFFECTIVE}，见
 * {@code docs/技术文档/policyStatus双码映射对照表.md}。
 * </p>
 *
 * <p>
 * 🔴 <b>取值依据</b>（不是拍脑袋的常量表）：
 * </p>
 * <ul>
 * <li><b>状态类 4 类</b>（{@code policyMaintenanceAction() != NONE}）——准入集合由<b>保单域状态机</b>
 * {@code PolicyStatus.validateTransition()} 唯一确定，无裁量空间：中止仅 {@code EFFECTIVE}、
 * 恢复仅 {@code SUSPENDED}、复效仅 {@code LAPSED}、终止为
 * {@code EFFECTIVE/SUSPENDED/LAPSED}（状态机中这三态均可 → {@code TERMINATED}，
 * 而 {@code NOT_EFFECTIVE} 只能 → {@code EFFECTIVE}/{@code CANCELLED}，不可终止）。</li>
 * <li><b>字段/单证/受限类 22 类</b>——取 {@code {EFFECTIVE}}：合同须处于生效态才能承载变更。此值与既有 8 类中
 * 7 个字段类的已签字口径完全一致（g04-07 裁示：字段类统一保守取 {@code {EFFECTIVE}}；g04-09 的挂失/补发/
 * 解除挂失 3 类沿用同一口径）。</li>
 * </ul>
 *
 * <p>
 * 🔴 <b>本矩阵是准入的<b>必要</b>条件而非充分条件</b>：独立建案路径上的产品 Offering
 * {@code allowedPolicyStatuses} 仍作为<b>额外收窄</b>叠加（两者都通过才受理）。领域中立的准入结论只在
 * 本矩阵，产品配置不得表达与领域状态机相冲突的组合。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/29
 */
public final class MaintenanceAdmissionMatrix {

    /**
     * 矩阵必须登记的保全类型条数——{@link MaintenanceType} 的<b>绝对下界</b>。
     * <p>
     * 测试以本常量与枚举实际值数<b>双向断言</b>（{@code == 23} 且 {@code == MaintenanceType.values().length}），
     * 枚举新增类型而未登记即失败；断言取绝对值而非「当前值 + 1」，避免漏登记被相对口径掩盖。
     * </p>
     */
    public static final int REGISTERED_TYPE_COUNT = 26;

    /** 字段类与「中止」类的准入集合：合同须处于生效态。 */
    private static final Set<PolicyStatus> ACTIVE_ONLY = Set.of(PolicyStatus.EFFECTIVE);

    /** 复效的准入集合：仅失效保单。 */
    private static final Set<PolicyStatus> LAPSED_ONLY = Set.of(PolicyStatus.LAPSED);

    /** 恢复的准入集合：仅暂停保单。 */
    private static final Set<PolicyStatus> SUSPENDED_ONLY = Set.of(PolicyStatus.SUSPENDED);

    /** 终止的准入集合：保单域状态机中三态均可 → TERMINATED。 */
    private static final Set<PolicyStatus> TERMINATABLE = Set.of(
            PolicyStatus.EFFECTIVE, PolicyStatus.SUSPENDED, PolicyStatus.LAPSED);

    private static final Map<MaintenanceType, MaintenanceAdmissionRule> RULES = rules();

    private MaintenanceAdmissionMatrix() {
    }

    /**
     * 取指定保全类型的准入规则。
     *
     * @param type 保全类型
     * @return 准入规则；未登记时返回 {@code null}（拒绝由 {@link #require} 显式表达）
     */
    public static MaintenanceAdmissionRule ruleOf(MaintenanceType type) {
        return type == null ? null : RULES.get(type);
    }

    /**
     * 判定 (保全类型, 保单状态) 组合是否准入。
     *
     * @param type 保全类型
     * @param status 保单状态（读模型词表）
     * @return 准入返回 true；类型未登记、状态为 null 或不在取值内一律返回 false
     */
    public static boolean permits(MaintenanceType type, PolicyStatus status) {
        MaintenanceAdmissionRule rule = ruleOf(type);
        return rule != null && rule.permits(status);
    }

    /**
     * 准入闸口：不在准入取值内即抛异常（fail-closed）。
     *
     * @param type 保全类型
     * @param status 保单状态（读模型词表）
     * @throws MaintenancePolicyStatusNotAdmittedException 类型未登记或状态不在准入取值内
     */
    public static void require(MaintenanceType type, PolicyStatus status) {
        MaintenanceAdmissionRule rule = ruleOf(type);
        if (rule == null) {
            throw new MaintenancePolicyStatusNotAdmittedException(
                    MaintenanceErrorCode.MAINTENANCE_POLICY_STATUS_NOT_ADMITTED,
                    "保全类型[" + type + "]尚未登记状态准入规则，按未登记即拒绝处理");
        }
        if (!rule.permits(status)) {
            throw new MaintenancePolicyStatusNotAdmittedException(
                    rule.rejectionCode(),
                    "保全项[" + type.getCode() + "]在当前保单状态[" + status + "]下不可办理，准入状态为"
                            + rule.admittedStatuses());
        }
    }

    /**
     * 已登记的全部保全类型（供守卫测试与体检报告核对）。
     *
     * @return 已登记类型集合
     */
    public static Set<MaintenanceType> registeredTypes() {
        return RULES.keySet();
    }

    /**
     * 准入矩阵登记表——26 条逐类显式登记，是「哪些保全项能在哪些保单状态下办理」的单一权威。
     *
     * <p>
     * 🔴 每条一行、逐类显式登记（不用推导函数批量生成）：`G04/AC-08` 要求「逐类有显式准入结论」，
     * 且登记条数须可机械计数（测试断言 {@link #REGISTERED_TYPE_COUNT}）。新增保全类型时<b>必须同批加一行</b>。
     * </p>
     */
    private static Map<MaintenanceType, MaintenanceAdmissionRule> rules() {
        Map<MaintenanceType, MaintenanceAdmissionRule> rules = new EnumMap<>(MaintenanceType.class);
        // ==================== 状态类 4 类：取值由保单域状态机唯一确定 ====================
        rules.put(MaintenanceType.POLICY_SUSPENSION, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.POLICY_RESUMPTION, admittedWhen(SUSPENDED_ONLY));
        rules.put(MaintenanceType.POLICY_REINSTATEMENT, lapsedOnlyLegacy());
        rules.put(MaintenanceType.POLICY_TERMINATION, admittedWhen(TERMINATABLE));
        // ==================== 字段类 19 类：合同须处于生效态 ====================
        // 既有 7 类沿用原拒绝码（对外契约不变）
        rules.put(MaintenanceType.POLICY_INFO_CHANGE, activeOnlyLegacy());
        rules.put(MaintenanceType.POLICY_PERIOD_CHANGE, activeOnlyLegacy());
        rules.put(MaintenanceType.COVERAGE_AMOUNT_CHANGE, activeOnlyLegacy());
        rules.put(MaintenanceType.INSURED_INFO_CHANGE, activeOnlyLegacy());
        rules.put(MaintenanceType.SUBJECT_CHANGE, activeOnlyLegacy());
        rules.put(MaintenanceType.SMOKING_STATUS_CHANGE, activeOnlyLegacy());
        rules.put(MaintenanceType.COVERAGE_CHANGE, activeOnlyLegacy());
        // g04-07 新纳入矩阵的 12 类（此前落 default 放行，无状态准入结论）
        rules.put(MaintenanceType.POLICY_HOLDER_CHANGE, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.BENEFICIARY_CHANGE, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.PAYMENT_METHOD_CHANGE, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.ADDITIONAL_PAYMENT, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.REDUCTION_PAYMENT, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.POLICY_REVERSAL, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.FUND_SWITCH, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.TOP_UP, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.PARTIAL_WITHDRAWAL, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.POLICY_LOAN, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.LOAN_REPAYMENT, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.REDUCED_PAID_UP, admittedWhen(ACTIVE_ONLY));
        // g04-09 单证与服务受限类 3 类：合同须处于生效态（沿用 g04-07 的保守口径）
        rules.put(MaintenanceType.LOSS_REPORT, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.POLICY_REISSUE, admittedWhen(ACTIVE_ONLY));
        rules.put(MaintenanceType.LOSS_REPORT_RELEASE, admittedWhen(ACTIVE_ONLY));
        return Map.copyOf(rules);
    }

    /** 既有字段类沿用的准入规则（拒绝码 {@code POLICY_NOT_ACTIVE}，对外契约不变）。 */
    private static MaintenanceAdmissionRule activeOnlyLegacy() {
        return new MaintenanceAdmissionRule(ACTIVE_ONLY, MaintenanceErrorCode.POLICY_NOT_ACTIVE);
    }

    /** 既有复效沿用的准入规则（拒绝码 {@code POLICY_NOT_TERMINATED}，对外契约不变）。 */
    private static MaintenanceAdmissionRule lapsedOnlyLegacy() {
        return new MaintenanceAdmissionRule(LAPSED_ONLY, MaintenanceErrorCode.POLICY_NOT_TERMINATED);
    }

    /** g04-07 新纳入矩阵的准入规则（统一拒绝码 {@code MAINTENANCE_POLICY_STATUS_NOT_ADMITTED}）。 */
    private static MaintenanceAdmissionRule admittedWhen(Set<PolicyStatus> admittedStatuses) {
        return new MaintenanceAdmissionRule(
                admittedStatuses, MaintenanceErrorCode.MAINTENANCE_POLICY_STATUS_NOT_ADMITTED);
    }
}
