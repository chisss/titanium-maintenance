package com.titanium.maintenance.query.handler.projection;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceExecutedEvent;
import com.titanium.maintenance.event.MaintenancePremiumCalculatedEvent;
import com.titanium.maintenance.event.MaintenanceStatusChangedEvent;
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

    /**
     * 投影保全创建事件：新建读模型记录
     */
    @EventHandler
    @Transactional
    public void on(MaintenanceCreatedEvent event) {
        log.info("[读模型投影] 保全创建: maintenanceId={}", event.maintenanceId().getId());

        MaintenanceView view = maintenanceViewRepository.findByMaintenanceId(event.maintenanceId().getId())
                .orElseGet(MaintenanceView::new);

        view.setMaintenanceId(event.maintenanceId().getId());
        view.setPolicyId(event.policyId().getId());
        view.setCustomerId(event.customerId().getId());
        view.setMaintenanceType(event.maintenanceType());
        view.setStatus(MaintenanceStatus.PENDING);
        view.setEffectiveTimeType(event.effectiveTimeType());
        view.setSpecificEffectiveDate(event.specificEffectiveDate());
        view.setDescription(event.description());
        if (view.getCreateTime() == null) {
            view.setCreateTime(event.createdAt());
        }
        view.setUpdateTime(event.createdAt());
        view.setTenantId(event.tenantId());

        maintenanceViewRepository.save(view);
    }

    /**
     * 投影保全状态变更事件
     */
    @EventHandler
    @Transactional
    public void on(MaintenanceStatusChangedEvent event) {
        log.info("[读模型投影] 保全状态变更: maintenanceId={}, {} -> {}", event.maintenanceId().getId(),
                event.oldStatus(), event.newStatus());

        maintenanceViewRepository.findByMaintenanceId(event.maintenanceId().getId()).ifPresentOrElse(view -> {
            view.setStatus(event.newStatus());
            view.setUpdateTime(event.changedAt());
            maintenanceViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 保全状态变更失败：未找到读模型记录 maintenanceId={}",
                event.maintenanceId().getId()));
    }

    /**
     * 投影保全保费计算事件
     */
    @EventHandler
    @Transactional
    public void on(MaintenancePremiumCalculatedEvent event) {
        log.info("[读模型投影] 保全保费计算: maintenanceId={}", event.maintenanceId().getId());

        maintenanceViewRepository.findByMaintenanceId(event.maintenanceId().getId()).ifPresentOrElse(view -> {
            view.setTotalAmount(event.totalAmount());
            view.setRefundAmount(event.refundAmount());
            view.setUpdateTime(event.updatedAt());
            maintenanceViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 保全保费计算失败：未找到读模型记录 maintenanceId={}",
                event.maintenanceId().getId()));
    }

    /**
     * 投影保全执行事件（流转至 COMPLETED）
     */
    @EventHandler
    @Transactional
    public void on(MaintenanceExecutedEvent event) {
        log.info("[读模型投影] 保全执行完成: maintenanceId={}", event.maintenanceId().getId());

        maintenanceViewRepository.findByMaintenanceId(event.maintenanceId().getId()).ifPresentOrElse(view -> {
            view.setStatus(MaintenanceStatus.COMPLETED);
            view.setUpdateTime(event.updatedAt());
            maintenanceViewRepository.save(view);
        }, () -> log.warn("[读模型投影] 保全执行失败：未找到读模型记录 maintenanceId={}",
                event.maintenanceId().getId()));
    }
}
