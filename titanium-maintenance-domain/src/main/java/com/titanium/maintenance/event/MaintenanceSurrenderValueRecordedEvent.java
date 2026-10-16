package com.titanium.maintenance.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.port.product.ProductSurrenderValuePort.SurrenderFactorFact;
import com.titanium.maintenance.valueobject.MaintenanceId;

/**
 * Product 退保价值策略证据已记录。
 *
 * <p>
 * 🔴 {@code factors}（g05-06）是 Product 退费明细的防腐镜像，随事件流下发供读侧冻结成
 * {@code t_maintenance_surrender_factor_view}。本域不以它做任何金额重算或写侧判定
 * （退费金额的权威仍在 Product 域，见 {@code ProductSurrenderValuePort}）。
 * </p>
 * <p>
 * 🔴 <b>尾部新增分量的事件兼容语义</b>：本事件已落 Axon 事件存储，早于本分量写入的**历史事件**
 * 反序列化后 {@code factors} 为 {@code null}（Jackson 对 record 缺失分量传 null）⇒ 消费方
 * <b>必须把 null 与空列表同等看待</b>（都表示「该次记录无明细」），不得据此抛异常，否则存量案件
 * 的投影重建会整批失败。
 * </p>
 *
 * @param factors 退费因子明细（历史事件为 {@code null}；新事件为空列表表示「确实无明细」）
 */
@Revision("1")
public record MaintenanceSurrenderValueRecordedEvent(
        MaintenanceId maintenanceId,
        String adjustmentId,
        String policyCode,
        String policyVersion,
        String policyContentHash,
        Integer policyYear,
        Integer coolingOffDays,
        String refundType,
        Boolean withinCoolingOff,
        BigDecimal cashValueRate,
        BigDecimal retainedCustomerAmount,
        BigDecimal internalCostRetentionRate,
        LocalDateTime recordedAt,
        String updatedBy,
        String tenantId,
        List<SurrenderFactorFact> factors) {
}
