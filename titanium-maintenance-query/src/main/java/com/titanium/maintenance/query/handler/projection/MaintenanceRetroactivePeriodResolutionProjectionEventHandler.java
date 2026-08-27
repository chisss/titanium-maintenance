package com.titanium.maintenance.query.handler.projection;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionFailedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionStartedEvent;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolutionLine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 投影关闭会计期间处理摘要和逐期间结转结果。 */
@Slf4j
@Component
@ProcessingGroup("maintenance-query-group")
@RequiredArgsConstructor
public class MaintenanceRetroactivePeriodResolutionProjectionEventHandler {

    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceRetroactivePeriodAdjustmentViewRepository periodRepository;

    @EventHandler
    @Transactional
    public void on(MaintenanceRetroactivePeriodResolutionStartedEvent event) {
        updateCase(event.maintenanceId().id(), event.tenantId(), event.resolution(), event.startedBy());
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceRetroactivePeriodResolutionCompletedEvent event) {
        updateCase(event.maintenanceId().id(), event.tenantId(), event.resolution(), event.completedBy());
        updatePeriods(event.maintenanceId().id(), event.tenantId(), event.resolution());
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceRetroactivePeriodResolutionFailedEvent event) {
        updateCase(event.maintenanceId().id(), event.tenantId(), event.resolution(), event.failedBy());
    }

    private void updateCase(
            String maintenanceId,
            String tenantId,
            MaintenanceRetroactivePeriodResolution resolution,
            String operatorId) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .ifPresentOrElse(view -> {
                    apply(view, resolution, operatorId);
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[关闭期间处理投影] 未找到案件 maintenanceId={}", maintenanceId));
    }

    private void apply(
            MaintenanceView view,
            MaintenanceRetroactivePeriodResolution resolution,
            String operatorId) {
        var evidence = resolution.evidence();
        view.setRetroactivePeriodResolutionId(resolution.periodResolutionId());
        view.setRetroactivePeriodResolutionOperationId(resolution.operationId());
        view.setRetroactivePeriodResolutionRequestHash(resolution.requestHash());
        view.setRetroactivePeriodResolutionStatus(resolution.status());
        view.setRetroactiveBillingResolutionId(evidence == null ? null : evidence.billingResolutionId());
        view.setRetroactivePeriodResolutionSourceBatchHash(resolution.sourceBatchResultHash());
        view.setRetroactivePeriodResolutionTargetPeriod(resolution.targetAccountingPeriod());
        view.setRetroactivePeriodResolutionResolvedLineCount(
                evidence == null ? 0 : evidence.resolvedLineCount());
        view.setRetroactivePeriodResolutionResultHash(evidence == null ? null : evidence.resultHash());
        view.setRetroactivePeriodResolutionReason(resolution.reason());
        view.setRetroactivePeriodResolutionFailureCode(resolution.failureCode());
        view.setRetroactivePeriodResolutionFailureMessage(resolution.failureMessage());
        view.setRetroactivePeriodResolutionStartedAt(resolution.startedAt());
        view.setRetroactivePeriodResolutionCompletedAt(resolution.completedAt());
        view.setRetroactivePeriodResolutionUpdatedAt(resolution.updatedAt());
        view.setUpdatedBy(operatorId);
        view.setUpdateTime(resolution.updatedAt());
    }

    private void updatePeriods(
            String maintenanceId,
            String tenantId,
            MaintenanceRetroactivePeriodResolution resolution) {
        var evidence = resolution.evidence();
        if (evidence == null) {
            return;
        }
        Map<String, MaintenanceRetroactivePeriodResolutionLine> lines = evidence.lines().stream()
                .collect(Collectors.toMap(
                        MaintenanceRetroactivePeriodResolutionLine::periodId, Function.identity()));
        var periods = periodRepository
                .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                        tenantId, maintenanceId, currentPeriodRecalculationId(maintenanceId, tenantId));
        long matchedCount = periods.stream().filter(period -> lines.containsKey(period.getPeriodId())).count();
        if (matchedCount != lines.size()) {
            throw new IllegalStateException("关闭期间处理明细无法与当前期间投影完整勾稽");
        }
        periods.forEach(period -> applyLine(period, lines.get(period.getPeriodId()), resolution));
        periodRepository.saveAll(periods);
    }

    private String currentPeriodRecalculationId(String maintenanceId, String tenantId) {
        return maintenanceViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .map(MaintenanceView::getRetroactivePeriodRecalculationId)
                .orElseThrow(() -> new IllegalStateException("关闭期间处理缺少期间重算投影"));
    }

    private void applyLine(
            MaintenanceRetroactivePeriodAdjustmentView period,
            MaintenanceRetroactivePeriodResolutionLine line,
            MaintenanceRetroactivePeriodResolution resolution) {
        if (line == null) {
            return;
        }
        if (!line.sourceLineResultHash().equals(period.getBillingResultHash())) {
            throw new IllegalStateException("关闭期间处理明细与当前Billing期间摘要不一致");
        }
        period.setTargetAccountingPeriod(line.targetAccountingPeriod());
        period.setResolutionStatus(resolution.status().getCode());
        period.setPostingReference(line.postingReference());
        period.setResolutionResultHash(line.resultHash());
        period.setUpdateTime(resolution.updatedAt());
    }
}
