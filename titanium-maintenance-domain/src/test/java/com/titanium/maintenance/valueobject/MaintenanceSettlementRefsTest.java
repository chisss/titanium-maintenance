package com.titanium.maintenance.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.PaymentRefundStatus;

/**
 * 保全资金结算外部引用值对象测试（m25-02 DSN-01b）
 * <p>
 * 覆盖 {@link MaintenanceSettlementRefs#empty()} 的空值语义与三个 wither（{@code withAdjustment} /
 * {@code withPosting} / {@code withRefund}）的<b>合并语义</b>——各 wither 保留其余分量、只覆盖本事件携带分量。
 * 聚合回放（含快照 + 增量重放）依赖这一性质：三个记录事件按序渐进填充，任一事件上合并都得到同一结果。
 * 值对象可 {@code new} 直测，不启动容器。
 * </p>
 */
class MaintenanceSettlementRefsTest {

    @Test
    @DisplayName("空引用集：8 个分量全为空（各分量判空即「未记录」）")
    void shouldExposeEmptyRefsWithAllNullComponents() {
        MaintenanceSettlementRefs empty = MaintenanceSettlementRefs.empty();

        assertNull(empty.originalCalculationId());
        assertNull(empty.replacementCalculationId());
        assertNull(empty.premiumAdjustmentId());
        assertNull(empty.premiumAdjustmentResultHash());
        assertNull(empty.billingPostingId());
        assertNull(empty.refundInstructionId());
        assertNull(empty.refundOrderId());
        assertNull(empty.refundStatus());
    }

    @Test
    @DisplayName("withAdjustment：只填充 Product 差额 4 分量，其余（入账/退款）保持原值")
    void shouldFillOnlyAdjustmentComponentsOnWithAdjustment() {
        MaintenanceSettlementRefs refs = MaintenanceSettlementRefs.empty()
                .withAdjustment("calc-original", "calc-replacement", "adjustment-1", "hash-1");

        assertEquals("calc-original", refs.originalCalculationId());
        assertEquals("calc-replacement", refs.replacementCalculationId());
        assertEquals("adjustment-1", refs.premiumAdjustmentId());
        assertEquals("hash-1", refs.premiumAdjustmentResultHash());
        assertNull(refs.billingPostingId());
        assertNull(refs.refundInstructionId());
        assertNull(refs.refundOrderId());
        assertNull(refs.refundStatus());
    }

    @Test
    @DisplayName("withPosting：只覆盖入账 ID，差额 4 分量保持不变")
    void shouldPreserveAdjustmentComponentsOnWithPosting() {
        MaintenanceSettlementRefs refs = MaintenanceSettlementRefs.empty()
                .withAdjustment("calc-original", "calc-replacement", "adjustment-1", "hash-1")
                .withPosting("posting-1");

        assertEquals("posting-1", refs.billingPostingId());
        assertEquals("calc-original", refs.originalCalculationId(), "差额分量不得被入账合并清空");
        assertEquals("calc-replacement", refs.replacementCalculationId());
        assertEquals("adjustment-1", refs.premiumAdjustmentId());
        assertEquals("hash-1", refs.premiumAdjustmentResultHash());
        assertNull(refs.refundStatus());
    }

    @Test
    @DisplayName("withRefund：只覆盖退款 3 分量，差额与入账分量保持不变")
    void shouldPreservePriorComponentsOnWithRefund() {
        MaintenanceSettlementRefs refs = MaintenanceSettlementRefs.empty()
                .withAdjustment("calc-original", "calc-replacement", "adjustment-1", "hash-1")
                .withPosting("posting-1")
                .withRefund("refund-instruction-1", "refund-order-1", PaymentRefundStatus.SUCCEEDED);

        assertEquals("refund-instruction-1", refs.refundInstructionId());
        assertEquals("refund-order-1", refs.refundOrderId());
        assertEquals(PaymentRefundStatus.SUCCEEDED, refs.refundStatus());
        assertEquals("calc-original", refs.originalCalculationId(), "退款合并不得清空差额分量");
        assertEquals("adjustment-1", refs.premiumAdjustmentId());
        assertEquals("posting-1", refs.billingPostingId(), "退款合并不得清空入账分量");
    }

    @Test
    @DisplayName("渐进填充（adjustment → posting → refund）与一次性全参构造结果一致（快照+增量重放等价）")
    void shouldProduceSameResultAsFullConstructionWhenFilledIncrementally() {
        MaintenanceSettlementRefs incremental = MaintenanceSettlementRefs.empty()
                .withAdjustment("calc-original", "calc-replacement", "adjustment-1", "hash-1")
                .withPosting("posting-1")
                .withRefund("refund-instruction-1", "refund-order-1", PaymentRefundStatus.SUCCEEDED);

        MaintenanceSettlementRefs single = new MaintenanceSettlementRefs("calc-original", "calc-replacement",
                "adjustment-1", "hash-1", "posting-1", "refund-instruction-1", "refund-order-1",
                PaymentRefundStatus.SUCCEEDED);

        assertEquals(single, incremental, "三步渐进合并的结果必须与一次性全参构造逐分量相等");
    }

    @Test
    @DisplayName("withRefund 传 null 状态：合法载荷（未收录码归一后的形态），不抛异常")
    void shouldAcceptNullRefundStatusOnWithRefund() {
        MaintenanceSettlementRefs refs = MaintenanceSettlementRefs.empty()
                .withRefund("refund-instruction-1", "refund-order-1", null);

        assertEquals("refund-instruction-1", refs.refundInstructionId());
        assertNull(refs.refundStatus(), "未收录的外部退款状态码经 fromCode 归一为 null 后存入");
    }
}
