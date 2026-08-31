package com.titanium.maintenance.query.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.titanium.maintenance.event.MaintenanceCaseRejectedByReviewEvent;
import com.titanium.maintenance.event.MaintenanceCaseRejectedByUnderwritingEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceEffectCompensationRequiredEvent;
import com.titanium.maintenance.event.MaintenanceEffectCompensationResolvedEvent;
import com.titanium.maintenance.event.MaintenanceExecutedEvent;
import com.titanium.maintenance.event.MaintenanceFinancialSettlementRecordedEvent;
import com.titanium.maintenance.event.MaintenancePremiumAdjustmentRecordedEvent;
import com.titanium.maintenance.event.MaintenancePremiumCalculatedEvent;
import com.titanium.maintenance.event.MaintenancePremiumPostingRecordedEvent;
import com.titanium.maintenance.event.MaintenanceStatusChangedEvent;
import com.titanium.maintenance.event.MaintenanceSurrenderValueRecordedEvent;
import com.titanium.maintenance.query.view.MaintenanceView;

/**
 * 保全读模型投影映射器（MapStruct，事件 → 读模型字段拷贝）
 * <p>
 * 承担投影处理器中「纯字段/值对象结构拷贝」类投影的 event record → View 映射，取代逐字段 set：
 * 创建类事件用 {@link NullValuePropertyMappingStrategy#IGNORE} 保留 upsert 语义；其余事件用
 * {@link NullValuePropertyMappingStrategy#SET_TO_NULL} 精确还原「事件缺省字段覆盖 View 既有值」的
 * 投影写入语义。
 * </p>
 * <p>
 * <b>职责边界</b>：仅做纯结构翻译（值对象拆为其 id 字符串、状态常量置位、updatedBy/updateTime 取事件侧字段）。
 * 含业务分流的映射（如差额方向决定金额归集与结算状态）与含"仅首次"语义的审计时间戳仍由投影处理器控制，
 * 不下沉映射器。
 * </p>
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MaintenanceViewMapper {

    /** 保全创建事件 → 保全读模型（就地 upsert；值对象拆解、状态置 PENDING、updatedBy 取 createdBy） */
    @Mapping(target = "maintenanceId", source = "maintenanceId.id")
    @Mapping(target = "policyId", source = "policyId.id")
    @Mapping(target = "customerId", source = "customerId.id")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "premiumSettlementStatus", constant = "NOT_STARTED")
    @Mapping(target = "commissionAdjustmentCount", constant = "0")
    @Mapping(target = "balanceAmount", constant = "0")
    @Mapping(target = "updatedBy", source = "createdBy")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void applyCreated(@MappingTarget MaintenanceView view, MaintenanceCreatedEvent event);

    /** 生效补偿记录事件 → 读模型（evidence 拆解、置补偿标记、updatedBy/updateTime 取事件侧） */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "effectCompensationRequired", constant = "true")
    @Mapping(target = "effectCompensationId", source = "evidence.compensationId")
    @Mapping(target = "effectCompensationRequestId", source = "evidence.requestId")
    @Mapping(target = "effectCompensationEndorsementNo", source = "evidence.endorsementNo")
    @Mapping(target = "effectCompensationPolicyVersion", source = "evidence.actualPolicyVersion")
    @Mapping(target = "effectCompensationApplicationHash", source = "evidence.applicationHash")
    @Mapping(target = "effectCompensationReason", source = "evidence.failureReason")
    @Mapping(target = "effectCompensationRecordedAt", source = "evidence.recordedAt")
    @Mapping(target = "updatedBy", source = "recordedBy")
    @Mapping(target = "updateTime", source = "recordedAt")
    void applyEffectCompensationRequired(@MappingTarget MaintenanceView view,
            MaintenanceEffectCompensationRequiredEvent event);

    /** 生效补偿勾稽成功事件 → 读模型（关闭补偿标记） */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "effectCompensationRequired", constant = "false")
    @Mapping(target = "effectCompensationResolvedAt", source = "resolvedAt")
    @Mapping(target = "effectCompensationResolvedBy", source = "resolvedBy")
    @Mapping(target = "updatedBy", source = "resolvedBy")
    @Mapping(target = "updateTime", source = "resolvedAt")
    void applyEffectCompensationResolved(@MappingTarget MaintenanceView view,
            MaintenanceEffectCompensationResolvedEvent event);

    /** 审核拒绝事件 → 读模型（状态置终态 REJECTED） */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "status", constant = "REJECTED")
    @Mapping(target = "updatedBy", source = "rejectedBy")
    @Mapping(target = "updateTime", source = "rejectedAt")
    void applyRejectedByReview(@MappingTarget MaintenanceView view, MaintenanceCaseRejectedByReviewEvent event);

    /** 核保拒绝事件 → 读模型（状态置终态 REJECTED） */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "status", constant = "REJECTED")
    @Mapping(target = "updatedBy", source = "rejectedBy")
    @Mapping(target = "updateTime", source = "rejectedAt")
    void applyRejectedByUnderwriting(@MappingTarget MaintenanceView view,
            MaintenanceCaseRejectedByUnderwritingEvent event);

    /** 保全状态变更事件 → 读模型 */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "status", source = "newStatus")
    @Mapping(target = "updatedBy", source = "changedBy")
    @Mapping(target = "updateTime", source = "changedAt")
    void applyStatusChanged(@MappingTarget MaintenanceView view, MaintenanceStatusChangedEvent event);

    /** 保全保费计算事件 → 读模型（金额归集） */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "totalAmount", source = "totalAmount")
    @Mapping(target = "refundAmount", source = "refundAmount")
    @Mapping(target = "updatedBy", source = "updatedBy")
    @Mapping(target = "updateTime", source = "updatedAt")
    void applyPremiumCalculated(@MappingTarget MaintenanceView view, MaintenancePremiumCalculatedEvent event);

    /** Product 生命周期差额检查点事件 → 读模型（纯拷贝部分；金额分流业务规则留处理器） */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "originalCalculationId", source = "originalCalculationId")
    @Mapping(target = "replacementCalculationId", source = "replacementCalculationId")
    @Mapping(target = "premiumAdjustmentId", source = "adjustmentId")
    @Mapping(target = "premiumAdjustmentResultHash", source = "adjustmentResultHash")
    @Mapping(target = "balanceDirection", source = "direction")
    @Mapping(target = "balanceAmount", source = "amount")
    @Mapping(target = "balanceCurrency", source = "currency")
    @Mapping(target = "updatedBy", source = "updatedBy")
    @Mapping(target = "updateTime", source = "recordedAt")
    void applyPremiumAdjustmentCheckpoint(@MappingTarget MaintenanceView view,
            MaintenancePremiumAdjustmentRecordedEvent event);

    /** Product 退保价值策略证据事件 → 读模型（字段名带 surrender 前缀映射） */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "surrenderPolicyCode", source = "policyCode")
    @Mapping(target = "surrenderPolicyVersion", source = "policyVersion")
    @Mapping(target = "surrenderPolicyContentHash", source = "policyContentHash")
    @Mapping(target = "surrenderPolicyYear", source = "policyYear")
    @Mapping(target = "coolingOffDays", source = "coolingOffDays")
    @Mapping(target = "surrenderRefundType", source = "refundType")
    @Mapping(target = "withinCoolingOff", source = "withinCoolingOff")
    @Mapping(target = "cashValueRate", source = "cashValueRate")
    @Mapping(target = "retainedCustomerAmount", source = "retainedCustomerAmount")
    @Mapping(target = "internalCostRetentionRate", source = "internalCostRetentionRate")
    @Mapping(target = "updatedBy", source = "updatedBy")
    @Mapping(target = "updateTime", source = "recordedAt")
    void applySurrenderValueRecorded(@MappingTarget MaintenanceView view,
            MaintenanceSurrenderValueRecordedEvent event);

    /** Billing 生命周期余额入账事件 → 读模型（结算状态置 POSTED） */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "billingPostingId", source = "postingId")
    @Mapping(target = "premiumSettlementStatus", constant = "POSTED")
    @Mapping(target = "updatedBy", source = "updatedBy")
    @Mapping(target = "updateTime", source = "recordedAt")
    void applyPremiumPostingRecorded(@MappingTarget MaintenanceView view,
            MaintenancePremiumPostingRecordedEvent event);

    /** Billing 资金结算检查点事件 → 读模型 */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "refundInstructionId", source = "refundInstructionId")
    @Mapping(target = "refundOrderId", source = "refundOrderId")
    @Mapping(target = "refundStatus", source = "refundStatus")
    @Mapping(target = "commissionAdjustmentCount", source = "commissionAdjustmentCount")
    @Mapping(target = "premiumSettlementStatus", source = "premiumSettlementStatus")
    @Mapping(target = "updatedBy", source = "updatedBy")
    @Mapping(target = "updateTime", source = "recordedAt")
    void applyFinancialSettlementRecorded(@MappingTarget MaintenanceView view,
            MaintenanceFinancialSettlementRecordedEvent event);

    /** 保全执行事件 → 读模型（状态置终态 COMPLETED） */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "maintenanceId", ignore = true)
    @Mapping(target = "status", constant = "COMPLETED")
    @Mapping(target = "updatedBy", source = "updatedBy")
    @Mapping(target = "updateTime", source = "updatedAt")
    void applyExecuted(@MappingTarget MaintenanceView view, MaintenanceExecutedEvent event);
}
