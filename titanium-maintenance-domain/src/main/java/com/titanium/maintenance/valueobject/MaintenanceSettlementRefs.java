package com.titanium.maintenance.valueobject;

import com.titanium.maintenance.common.enums.PaymentRefundStatus;

/**
 * 保全资金结算外部引用值对象（Product/Billing 单据与退款事实的聚合内聚引用集）
 * <p>
 * 收敛聚合中 8 个平铺的外部单据引用字段（原/新计算 ID、差额事实 ID 与结果哈希、Billing 入账 ID、
 * 退款指令/退款单 ID、退款状态）。三个记录事件（差额/入账/资金结算）按序渐进填充本值对象——
 * 各 {@code withXxx} 方法均为「保留其余分量、只覆盖本事件携带分量」的合并语义，聚合回放
 * （含快照 + 增量重放）在其中任一事件上合并都得到同一结果。
 * </p>
 * <p>
 * 🔴 {@code refundStatus} 由 String 收敛为 {@link PaymentRefundStatus} 枚举：外部（Payment 域）
 * 未收录的码值经 {@code fromCode} 归一为 {@code null}（该枚举契约明示「未收录的码值按未知处理，
 * 结算状态保持 PENDING」）——未知码不再保留原字符串，这是本收敛的既定取舍；幂等比较两侧
 * 同经 {@code fromCode}，未知码「已记录—重放同一未知码」仍判等价（与原字符串比较语义一致）。
 * </p>
 *
 * @param originalCalculationId 原保费计算 ID（Product 差额事实来源）
 * @param replacementCalculationId 新保费计算 ID（Product 差额事实来源）
 * @param premiumAdjustmentId 差额事实 ID
 * @param premiumAdjustmentResultHash 差额结果哈希（与入账/结算来源比对）
 * @param billingPostingId Billing 入账 ID
 * @param refundInstructionId 退款指令 ID
 * @param refundOrderId 退款单 ID
 * @param refundStatus 退款状态（Payment 域状态码的域内镜像枚举，未收录码归一 null）
 */
public record MaintenanceSettlementRefs(String originalCalculationId, String replacementCalculationId,
        String premiumAdjustmentId, String premiumAdjustmentResultHash, String billingPostingId,
        String refundInstructionId, String refundOrderId, PaymentRefundStatus refundStatus) {

    /** 空引用集（未记录任何外部单据事实）；各分量判空即「未记录」 */
    private static final MaintenanceSettlementRefs EMPTY = new MaintenanceSettlementRefs(null, null, null, null, null,
            null, null, null);

    /**
     * 空引用集工厂：聚合在未经历任一结算记录事件时按本值读取，调用方无需对聚合字段判空。
     */
    public static MaintenanceSettlementRefs empty() {
        return EMPTY;
    }

    /** 合并 Product 差额事实（原/新计算 ID + 差额 ID + 结果哈希），保留其余分量 */
    public MaintenanceSettlementRefs withAdjustment(String originalCalculationId, String replacementCalculationId,
            String premiumAdjustmentId, String premiumAdjustmentResultHash) {
        return new MaintenanceSettlementRefs(originalCalculationId, replacementCalculationId, premiumAdjustmentId,
                premiumAdjustmentResultHash, billingPostingId, refundInstructionId, refundOrderId, refundStatus);
    }

    /** 合并 Billing 入账事实（入账 ID），保留其余分量 */
    public MaintenanceSettlementRefs withPosting(String billingPostingId) {
        return new MaintenanceSettlementRefs(originalCalculationId, replacementCalculationId, premiumAdjustmentId,
                premiumAdjustmentResultHash, billingPostingId, refundInstructionId, refundOrderId, refundStatus);
    }

    /** 合并退款事实（退款指令/退款单/退款状态），保留其余分量 */
    public MaintenanceSettlementRefs withRefund(String refundInstructionId, String refundOrderId,
            PaymentRefundStatus refundStatus) {
        return new MaintenanceSettlementRefs(originalCalculationId, replacementCalculationId, premiumAdjustmentId,
                premiumAdjustmentResultHash, billingPostingId, refundInstructionId, refundOrderId, refundStatus);
    }
}
