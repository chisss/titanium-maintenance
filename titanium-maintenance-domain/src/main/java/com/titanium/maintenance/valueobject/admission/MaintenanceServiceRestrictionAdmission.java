package com.titanium.maintenance.valueobject.admission;

import java.util.Collection;
import java.util.Set;

import com.titanium.maintenance.common.exception.MaintenanceServiceRestrictedException;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 服务受限准入矩阵——「保单受限状态 × 保全类型」能否办理的<b>单一权威</b>（准入的第二维度）。
 *
 * <p>
 * 与 {@link MaintenanceAdmissionMatrix}（保全类型 × 保单状态）是<b>两条正交维度</b>，故分立两个类而非并入同一
 * 张表：前者判定合同处于哪一生命周期阶段，本矩阵判定合同是否被挂失等受限标记罩住。受限与
 * {@code PolicyStatus} 正交——挂失保单仍是 {@code EFFECTIVE}，状态准入看不出任何异常，缺此维度则「已挂失保单
 * 照常退保」被静默放行。
 * </p>
 *
 * <p>
 * 🔴 <b>本矩阵是准入的<b>必要</b>条件而非充分条件</b>：它只在<b>建案前置</b>拒绝明显受限的案件，避免走完审批
 * 才在生效环节被拒。权威拒绝在保单域写侧（{@code Policy.requireNotServiceRestricted} 与
 * {@code PolicyLoanApplicationService.disburse}），且该判定取自<b>强一致的写模型聚合</b>；本矩阵只能读到建案
 * 时采集的快照字段（来自保单读模型，最终一致），故<b>不得</b>被当作权威——快照滞后时它会漏拒，正好由保单域
 * 兜住；反向（快照说有、实际已解除）则表现为「多拒」，由人工重新建案消解。
 * </p>
 *
 * <p>
 * 🔴 <b>受限挡住什么，只在本类登记一处</b>（{@link #INADMISSIBLE_WHEN_RESTRICTED}）。挂失的业务边界（R-5）
 * 尚未定界，本任务只落「登记 + 受限标记 + 退保/贷款准入」骨架，不预设更宽的冻结规则；后续扩面只需改该集合，
 * 两条建案路径与守卫计数会同步生效。
 * </p>
 *
 * <p>
 * 🔴 <b>解除挂失必须在受限期内可办理</b>：{@code LOSS_REPORT_RELEASE} 不在本矩阵的禁止集合内——若把它一并
 * 禁掉，保单一旦挂失即<b>永久无法解除</b>（受限 → 不得办理任何保全 → 受限永不消失），构成死锁。故本集合的
 * 每一处扩面都必须先自问「它是否会让受限态无法解除」。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/29
 */
public final class MaintenanceServiceRestrictionAdmission {

    /**
     * 服务受限期间<b>不得办理</b>的保全项——减损类（使保单价值外流的动作）。
     * <p>
     * 判定依据：受限（挂失）的实质是「合同权益可能已被第三方主张」，此期间任何令保单项下价值外流或使合同
     * 终了的动作都可能损害真实权利人的利益，故退保（{@code POLICY_TERMINATION}）与保单贷款
     * （{@code POLICY_LOAN}）须先解除受限。二者也已在保单域写侧各自设有权威守卫，两侧口径一致。
     * </p>
     */
    private static final Set<MaintenanceType> INADMISSIBLE_WHEN_RESTRICTED = Set.of(
            MaintenanceType.POLICY_TERMINATION,
            MaintenanceType.POLICY_LOAN);

    private MaintenanceServiceRestrictionAdmission() {
    }

    /**
     * 该保全项是否被服务受限挡住。
     *
     * @param type 保全类型
     * @return 受限期间不得办理返回 true；{@code null} 一律返回 false（类型缺失由状态准入矩阵拒绝，不在此处越权判定）
     */
    public static boolean blocks(MaintenanceType type) {
        return type != null && INADMISSIBLE_WHEN_RESTRICTED.contains(type);
    }

    /**
     * 受限期间不得办理的全部保全类型（供守卫测试与体检报告核对）。
     *
     * @return 禁止集合
     */
    public static Set<MaintenanceType> inadmissibleTypes() {
        return INADMISSIBLE_WHEN_RESTRICTED;
    }

    /**
     * 受限准入闸口：保单受限时对禁止集合内的保全项显式拒绝。
     *
     * <p>
     * 🔴 <b>失败关闭</b>：{@code serviceRestricted} 为 {@code null}（快照未回显受限维度）时按<b>未受限</b>
     * 放行——本闸口只是前置收窄，权威在保单域；把「未知」当作「受限」会把保单域的一次发布顺序问题放大成
     * 全体保全不可办理（可用性事故）。这一取舍与状态准入矩阵的 fail-closed 不同，因为那里的兜底方向是
     * 「拒绝无依据的组合」，而这里拒绝的依据本身就来自可能滞后的读模型。
     * </p>
     *
     * @param serviceRestricted 保单在建案快照时点是否处于服务受限之下
     * @param types 本次案件涉及的保全类型
     * @throws MaintenanceServiceRestrictedException 任一保全项在受限期间不得办理
     */
    public static void require(Boolean serviceRestricted, Collection<MaintenanceType> types) {
        if (!Boolean.TRUE.equals(serviceRestricted) || types == null || types.isEmpty()) {
            return;
        }
        types.stream()
                .filter(MaintenanceServiceRestrictionAdmission::blocks)
                .findFirst()
                .ifPresent(type -> {
                    throw new MaintenanceServiceRestrictedException(
                            MaintenanceErrorCode.MAINTENANCE_SERVICE_RESTRICTED,
                            "保单当前处于服务受限状态，受限期间不可办理保全项[" + type.getCode()
                                    + "]，请先解除受限后再办理");
                });
    }
}
