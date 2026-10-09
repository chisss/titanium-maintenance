package com.titanium.maintenance.command;

import java.math.BigDecimal;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.port.product.ProductSurrenderValuePort.SurrenderFactorFact;
import com.titanium.maintenance.valueobject.MaintenanceId;

/**
 * 记录 Product 已确认的退保价值策略证据。
 *
 * <p>
 * 🔴 {@code factors}（g05-06）是 Product 退费明细的**防腐镜像**，方向为「写侧事实 → 读模型」：
 * 本域不据此重算任何金额，也不参与退费口径判定，只把它透传进事件流，供读侧冻结成
 * {@code t_maintenance_surrender_factor_view}（对客展示与对账）。
 * 复用 {@link SurrenderFactorFact} 而非另建同形值对象——它已是本域对该明细的唯一表述，
 * 再造一个只会形成第二套口径（项目 CLAUDE.md §3.4.11）。
 * </p>
 * <p>
 * {@code factors} 置于**末位**：record 分量顺序即序列化顺序，尾插是事件/命令载荷演进的兼容形态。
 * </p>
 *
 * @param factors 退费因子明细（可能为空列表：历史调用方与兼容构造器不承载明细；不得为 {@code null}）
 */
public record RecordMaintenanceSurrenderValueCommand(
        @TargetAggregateIdentifier MaintenanceId id,
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
        String updatedBy,
        String tenantId,
        List<SurrenderFactorFact> factors) {

    /** 空明细归一 + 防御性拷贝：下游（事件、投影）无需再判空。 */
    public RecordMaintenanceSurrenderValueCommand {
        factors = factors == null ? List.of() : List.copyOf(factors);
    }
}
