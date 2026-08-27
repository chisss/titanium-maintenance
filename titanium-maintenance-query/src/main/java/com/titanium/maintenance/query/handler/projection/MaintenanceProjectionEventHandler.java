package com.titanium.maintenance.query.handler.projection;

import java.math.BigDecimal;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenancePremiumSettlementStatus;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
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
import com.titanium.maintenance.query.mapper.MaintenanceViewMapper;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保全域读模型投影事件处理器（CQRS 读侧核心）
 * <p>
 * 订阅 maintenance 域领域事件，投影到读模型表 {@code t_maintenance_view}，实现读写分离。 只做「事件 → 读模型」写入，
 * 不发命令、不持有 CommandGateway（读侧编排越界禁止）。
 * </p>
 * <p>
 * <b>处理组</b>：{@code maintenance-query-group}，读侧投影 + 查询处理器 + DLQ 三处一致。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("maintenance-query-group")
@RequiredArgsConstructor
public class MaintenanceProjectionEventHandler {

    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceViewMapper maintenanceViewMapper;

    /** 投影 Policy 已成功但案件回执待人工勾稽的补偿事实。 */
    @EventHandler
    @Transactional
    public void on(MaintenanceEffectCompensationRequiredEvent event) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
                    var evidence = event.evidence();
                    view.setEffectCompensationRequired(true);
                    view.setEffectCompensationId(evidence.compensationId());
                    view.setEffectCompensationRequestId(evidence.requestId());
                    view.setEffectCompensationEndorsementNo(evidence.endorsementNo());
                    view.setEffectCompensationPolicyVersion(evidence.actualPolicyVersion());
                    view.setEffectCompensationApplicationHash(evidence.applicationHash());
                    view.setEffectCompensationReason(evidence.failureReason());
                    view.setEffectCompensationRecordedAt(evidence.recordedAt());
                    view.setUpdatedBy(event.recordedBy());
                    view.setUpdateTime(event.recordedAt());
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[读模型投影] 生效补偿记录失败：未找到案件 maintenanceId={}",
                        event.maintenanceId().id()));
    }

    /** 幂等重试勾稽成功后关闭人工补偿标记。 */
    @EventHandler
    @Transactional
    public void on(MaintenanceEffectCompensationResolvedEvent event) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
                    view.setEffectCompensationRequired(false);
                    view.setEffectCompensationResolvedAt(event.resolvedAt());
                    view.setEffectCompensationResolvedBy(event.resolvedBy());
                    view.setUpdatedBy(event.resolvedBy());
                    view.setUpdateTime(event.resolvedAt());
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[读模型投影] 生效补偿关闭失败：未找到案件 maintenanceId={}",
                        event.maintenanceId().id()));
    }

    /** 审核拒绝将案件主投影同步置为终态。 */
    @EventHandler
    @Transactional
    public void on(MaintenanceCaseRejectedByReviewEvent event) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
                    view.setStatus(MaintenanceStatus.REJECTED);
                    view.setUpdatedBy(event.rejectedBy());
                    view.setUpdateTime(event.rejectedAt());
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[读模型投影] 审核拒绝失败：未找到案件 maintenanceId={}",
                        event.maintenanceId().id()));
    }

    /** 核保拒绝将案件主投影同步置为终态。 */
    @EventHandler
    @Transactional
    public void on(MaintenanceCaseRejectedByUnderwritingEvent event) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
                    view.setStatus(MaintenanceStatus.REJECTED);
                    view.setUpdatedBy(event.rejectedBy());
                    view.setUpdateTime(event.rejectedAt());
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[读模型投影] 核保拒绝失败：未找到案件 maintenanceId={}",
                        event.maintenanceId().id()));
    }

    /**
     * 投影保全创建事件：新建读模型记录
     */
    @EventHandler
    @Transactional
    public void on(MaintenanceCreatedEvent event) {
        log.info("[读模型投影] 保全创建: maintenanceId={}", event.maintenanceId().id());

        MaintenanceView view = maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                        event.maintenanceId().id(), event.tenantId())
                .orElseGet(MaintenanceView::new);

        // 事件字段 → 读模型的结构映射收敛到 MapStruct（值对象拆解、状态置 PENDING），消除逐字段 set
        maintenanceViewMapper.applyCreated(view, event);
        // 审计时间戳含"仅首次"语义，取事件发生时间，留投影处理器控制
        if (view.getCreateTime() == null) {
            view.setCreateTime(event.createdAt());
        }
        view.setUpdateTime(event.createdAt());

        maintenanceViewRepository.save(view);
    }

    /**
     * 投影保全状态变更事件
     */
    @EventHandler
    @Transactional
    public void on(MaintenanceStatusChangedEvent event) {
        log.info("[读模型投影] 保全状态变更: maintenanceId={}, {} -> {}", event.maintenanceId().id(),
                event.oldStatus(), event.newStatus());

        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
            view.setStatus(event.newStatus());
            view.setUpdatedBy(event.changedBy());
            view.setUpdateTime(event.changedAt());
            maintenanceViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 保全状态变更失败：未找到读模型记录 maintenanceId={}",
                event.maintenanceId().id()));
    }

    /**
     * 投影保全保费计算事件
     */
    @EventHandler
    @Transactional
    public void on(MaintenancePremiumCalculatedEvent event) {
        log.info("[读模型投影] 保全保费计算: maintenanceId={}", event.maintenanceId().id());

        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
            view.setTotalAmount(event.totalAmount());
            view.setRefundAmount(event.refundAmount());
            view.setUpdatedBy(event.updatedBy());
            view.setUpdateTime(event.updatedAt());
            maintenanceViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 保全保费计算失败：未找到读模型记录 maintenanceId={}",
                event.maintenanceId().id()));
    }

    /** 投影 Product 生命周期差额检查点。 */
    @EventHandler
    @Transactional
    public void on(MaintenancePremiumAdjustmentRecordedEvent event) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
                    view.setOriginalCalculationId(event.originalCalculationId());
                    view.setReplacementCalculationId(event.replacementCalculationId());
                    view.setPremiumAdjustmentId(event.adjustmentId());
                    view.setPremiumAdjustmentResultHash(event.adjustmentResultHash());
                    view.setBalanceDirection(event.direction());
                    view.setBalanceAmount(event.amount());
                    view.setBalanceCurrency(event.currency());
                    view.setTotalAmount(event.direction() == MaintenanceBalanceDirection.DEBIT
                            ? event.amount()
                            : BigDecimal.ZERO);
                    view.setRefundAmount(event.direction() == MaintenanceBalanceDirection.CREDIT
                            ? event.amount()
                            : BigDecimal.ZERO);
                    view.setPremiumSettlementStatus(event.direction() == MaintenanceBalanceDirection.NONE
                            ? MaintenancePremiumSettlementStatus.NOT_REQUIRED
                            : MaintenancePremiumSettlementStatus.ADJUSTMENT_CONFIRMED);
                    view.setUpdatedBy(event.updatedBy());
                    view.setUpdateTime(event.recordedAt());
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[读模型投影] Product 差额记录失败：未找到读模型 maintenanceId={}",
                        event.maintenanceId().id()));
    }

    /** 投影 Product 退保价值策略证据。 */
    @EventHandler
    @Transactional
    public void on(MaintenanceSurrenderValueRecordedEvent event) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
                    view.setSurrenderPolicyCode(event.policyCode());
                    view.setSurrenderPolicyVersion(event.policyVersion());
                    view.setSurrenderPolicyContentHash(event.policyContentHash());
                    view.setSurrenderPolicyYear(event.policyYear());
                    view.setCoolingOffDays(event.coolingOffDays());
                    view.setSurrenderRefundType(event.refundType());
                    view.setWithinCoolingOff(event.withinCoolingOff());
                    view.setCashValueRate(event.cashValueRate());
                    view.setRetainedCustomerAmount(event.retainedCustomerAmount());
                    view.setInternalCostRetentionRate(event.internalCostRetentionRate());
                    view.setUpdatedBy(event.updatedBy());
                    view.setUpdateTime(event.recordedAt());
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[读模型投影] 退保价值记录失败：未找到读模型 maintenanceId={}",
                        event.maintenanceId().id()));
    }

    /** 投影 Billing 生命周期余额检查点。 */
    @EventHandler
    @Transactional
    public void on(MaintenancePremiumPostingRecordedEvent event) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
                    view.setBillingPostingId(event.postingId());
                    view.setPremiumSettlementStatus(MaintenancePremiumSettlementStatus.POSTED);
                    view.setUpdatedBy(event.updatedBy());
                    view.setUpdateTime(event.recordedAt());
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[读模型投影] Billing 入账记录失败：未找到读模型 maintenanceId={}",
                        event.maintenanceId().id()));
    }

    /** 投影 Billing 资金结算检查点。 */
    @EventHandler
    @Transactional
    public void on(MaintenanceFinancialSettlementRecordedEvent event) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
                    view.setRefundInstructionId(event.refundInstructionId());
                    view.setRefundOrderId(event.refundOrderId());
                    view.setRefundStatus(event.refundStatus());
                    view.setCommissionAdjustmentCount(event.commissionAdjustmentCount());
                    view.setPremiumSettlementStatus(event.premiumSettlementStatus());
                    view.setUpdatedBy(event.updatedBy());
                    view.setUpdateTime(event.recordedAt());
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[读模型投影] Billing 资金结算记录失败：未找到读模型 maintenanceId={}",
                        event.maintenanceId().id()));
    }

    /**
     * 投影保全执行事件（流转至 COMPLETED）
     */
    @EventHandler
    @Transactional
    public void on(MaintenanceExecutedEvent event) {
        log.info("[读模型投影] 保全执行完成: maintenanceId={}", event.maintenanceId().id());

        maintenanceViewRepository.findByMaintenanceIdAndTenantId(
                event.maintenanceId().id(), event.tenantId()).ifPresentOrElse(view -> {
            view.setStatus(MaintenanceStatus.COMPLETED);
            view.setUpdatedBy(event.updatedBy());
            view.setUpdateTime(event.updatedAt());
            maintenanceViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 保全执行失败：未找到读模型记录 maintenanceId={}",
                event.maintenanceId().id()));
    }
}
