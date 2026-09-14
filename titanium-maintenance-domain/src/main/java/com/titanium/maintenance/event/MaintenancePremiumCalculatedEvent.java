package com.titanium.maintenance.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

/**
 * 保全保费试算完成事件（**人工金额，过渡事实**）
 * <p>
 * {@code totalAmount}/{@code refundAmount} 是保全员**手工试算**的补收/退还金额，**不是最终计费依据**：
 * 同一聚合的结构化生命周期差额（{@code RecordMaintenancePremiumAdjustmentCommand}，金额来自 Product 差额事实）
 * 会**覆盖这两个字段**，且结构化计价一旦开始，人工金额即被拒绝写入（见
 * {@code Maintenance#handle(CalculateMaintenancePremiumCommand)} 守卫与
 * {@code Maintenance#on(MaintenancePremiumAdjustmentRecordedEvent)}）。
 * </p>
 * <p>
 * 🔴 本事件虽经 Kafka 外发（主题 {@code maintenance-premium-calculated}），但**不应**作为跨域计费依据：
 * 客户补收/退还的权威通道是 maintenance → billing 的 Feign 结构化差额记账（{@code BillingPremiumLifecyclePort.post}），
 * 据本事件在 billing 建账单会与它**重复计量**同一笔补收。判定依据与未来接线的前置条件见
 * {@code docs/技术文档/跨域事件目录-2026-09.md} §六.12（2026-09-14 m6-904）。
 * </p>
 */
public record MaintenancePremiumCalculatedEvent(MaintenanceId maintenanceId, BigDecimal totalAmount,
                                                BigDecimal refundAmount, String calculationDetails,
                                                LocalDateTime updatedAt, String updatedBy, String tenantId) {
}
