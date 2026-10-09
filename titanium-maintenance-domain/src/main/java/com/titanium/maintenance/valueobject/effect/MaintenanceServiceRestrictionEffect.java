package com.titanium.maintenance.valueobject.effect;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionAction;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;

/**
 * 服务受限类保全项（挂失/解除挂失）的生效载荷：一条受限标记的施加或解除。
 * <p>
 * 与「字段变更」「金额事实」「合同状态动作」<b>并列</b>的第四种生效载荷。挂失这类保全项既无字段目录入口、
 * 也无金额、也不改合同状态（挂失保单仍是 {@code EFFECTIVE}），变更的实质就是这条受限标记本身——故它必须
 * 作为一等载荷递到保单域，而不是靠闸口放行后什么也不带。
 * </p>
 * <p>
 * 🔴 <b>不含生效时间</b>：受限生效时点恒取本次保全应用的 {@code effectiveAt}，另立字段会造出两处可漂移的
 * 时点，并让请求摘要多算一个同值字段（与 {@code MaintenanceAmountEffect} 不含时间的理由同源）。
 * </p>
 * <p>
 * 🔴 <b>不含保全项编码</b>：受限种类已由 {@link #type} 唯一确定该受限事实的身份，保全项与受限种类在
 * {@code MaintenanceItemCode} 目录中一一对应；再带一个 itemCode 只会造出「两个字段说同一件事」的
 * 可漂移面（金额载荷需要它是因为金额本身不含语义，受限种类本身即是语义）。
 * </p>
 * <p>
 * 🔴 <b>拒绝空交易是构造期不变量</b>：{@code action} 不得为 {@link PolicyServiceRestrictionAction#NONE}
 * ——「无受限动作」与「这是一条受限事实」自相矛盾；保单域侧同样以 {@code changesRestriction()} 把关。
 * </p>
 *
 * @param type   受限种类（当前仅挂失 {@code LOSS_REPORT}）
 * @param action 受限动作（{@code APPLY} 施加 / {@code RELEASE} 解除）
 */
public record MaintenanceServiceRestrictionEffect(
        PolicyServiceRestrictionType type,
        PolicyServiceRestrictionAction action) {

    public MaintenanceServiceRestrictionEffect {
        if (type == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceServiceRestrictionEffect", "type", "服务受限生效载荷必须指明受限种类");
        }
        if (action == null || !action.changesRestriction()) {
            throw new MaintenanceValidationException(
                    "MaintenanceServiceRestrictionEffect", "action",
                    "服务受限生效载荷必须给出施加或解除动作");
        }
    }

    /** 本条载荷是否为施加受限（否则为解除）。 */
    public boolean applying() {
        return action == PolicyServiceRestrictionAction.APPLY;
    }
}
