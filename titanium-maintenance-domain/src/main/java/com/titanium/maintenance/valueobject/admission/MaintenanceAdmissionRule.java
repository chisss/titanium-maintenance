package com.titanium.maintenance.valueobject.admission;

import java.util.Set;

import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 单条状态准入规则——某保全类型「允许在哪些保单状态下办理」及其拒绝时使用的错误码。
 *
 * <p>
 * 规则是<b>值对象</b>：只表达事实，不做判定编排。判定入口见 {@link MaintenanceAdmissionMatrix}。
 * </p>
 *
 * <p>
 * 🔴 <b>为何拒绝码是规则的一部分</b>：既有 8 类保全项（{@code POLICY_REINSTATEMENT} + 7 个字段类）对外
 * 已用 {@link MaintenanceErrorCode#POLICY_NOT_ACTIVE} / {@link MaintenanceErrorCode#POLICY_NOT_TERMINATED}
 * 表达拒绝，改码对已接入的调用方是破坏性变更；而新纳入矩阵的 15 类此前没有状态准入结论、无码可依。
 * 故把「拒绝码」随规则一并登记：<b>准入取值收敛到单一权威的同时，对外错误码契约保持不变</b>。
 * </p>
 *
 * @param admittedStatuses 允许办理的保单状态集合（不含任何状态即为不可办理，但登记时须显式写出取值）
 * @param rejectionCode 该规则拒绝时携带的错误码
 * @author wei.sun
 * @since 2026/9/29
 */
public record MaintenanceAdmissionRule(Set<PolicyStatus> admittedStatuses, MaintenanceErrorCode rejectionCode) {

    public MaintenanceAdmissionRule {
        if (admittedStatuses == null || admittedStatuses.isEmpty()) {
            throw new IllegalArgumentException("准入状态集合不能为空——不可办理的类型不应登记规则");
        }
        if (rejectionCode == null) {
            throw new IllegalArgumentException("准入规则的拒绝错误码不能为空");
        }
        admittedStatuses = Set.copyOf(admittedStatuses);
    }

    /**
     * 判定给定保单状态是否在准入取值内。
     *
     * <p>
     * 🔴 <b>失败关闭</b>：{@code status} 为 {@code null}（保单域未回显可识别状态）时返回 {@code false}，
     * 不得按「无法判定即放行」处理。
     * </p>
     *
     * @param status 保单状态（读模型词表 {@link PolicyStatus}）
     * @return 允许办理返回 true
     */
    public boolean permits(PolicyStatus status) {
        return status != null && admittedStatuses.contains(status);
    }
}
