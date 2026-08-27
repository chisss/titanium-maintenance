package com.titanium.maintenance.query.handler.projection;

import java.util.List;
import java.util.stream.Collectors;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisFailedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisStartedEvent;
import com.titanium.maintenance.query.repository.MaintenanceRetroactiveImpactItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceRetroactiveImpactItemView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 投影当前追溯影响分析摘要和结构化影响项。 */
@Slf4j
@Component
@ProcessingGroup("maintenance-query-group")
@RequiredArgsConstructor
public class MaintenanceRetroactiveImpactProjectionEventHandler {

    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceRetroactiveImpactItemViewRepository impactItemViewRepository;

    @EventHandler
    @Transactional
    public void on(MaintenanceRetroactiveImpactAnalysisStartedEvent event) {
        updateCase(event.maintenanceId().id(), event.tenantId(), event.analysis(), event.startedBy());
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceRetroactiveImpactAnalysisCompletedEvent event) {
        updateCase(event.maintenanceId().id(), event.tenantId(), event.analysis(), event.completedBy());
        replaceItems(event.maintenanceId().id(), event.tenantId(), event.analysis());
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceRetroactiveImpactAnalysisFailedEvent event) {
        updateCase(event.maintenanceId().id(), event.tenantId(), event.analysis(), event.failedBy());
    }

    private void updateCase(
            String maintenanceId,
            String tenantId,
            MaintenanceRetroactiveImpactAnalysis analysis,
            String operatorId) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .ifPresentOrElse(view -> {
                    apply(view, analysis, operatorId);
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[追溯影响投影] 未找到案件 maintenanceId={}", maintenanceId));
    }

    private void apply(
            MaintenanceView view,
            MaintenanceRetroactiveImpactAnalysis analysis,
            String operatorId) {
        view.setRetroactiveImpactAnalysisId(analysis.analysisId());
        view.setRetroactiveImpactAnalysisVersion(analysis.analysisVersion());
        view.setRetroactiveImpactOperationId(analysis.operationId());
        view.setRetroactiveImpactRequestHash(analysis.requestHash());
        view.setRetroactiveImpactScopeFrom(analysis.scopeFrom());
        view.setRetroactiveImpactScopeTo(analysis.scopeTo());
        view.setRetroactiveImpactStatus(analysis.status());
        view.setRetroactiveImpactCoveredDomains(analysis.coveredDomains().stream()
                .map(Enum::name).collect(Collectors.joining(",")));
        view.setRetroactiveImpactItemCount(analysis.items().size());
        view.setRetroactiveImpactBlockingCount(analysis.blockingItemCount());
        view.setRetroactiveImpactPendingCount(analysis.pendingItemCount());
        view.setRetroactiveImpactEvidenceVersion(analysis.evidenceVersion());
        view.setRetroactiveImpactResultHash(analysis.resultHash());
        view.setRetroactiveImpactFailureCode(analysis.failureCode());
        view.setRetroactiveImpactFailureMessage(analysis.failureMessage());
        view.setRetroactiveImpactStartedAt(analysis.startedAt());
        view.setRetroactiveImpactCompletedAt(analysis.completedAt());
        view.setRetroactiveImpactUpdatedAt(analysis.updatedAt());
        view.setUpdatedBy(operatorId);
        view.setUpdateTime(analysis.updatedAt());
    }

    private void replaceItems(
            String maintenanceId,
            String tenantId,
            MaintenanceRetroactiveImpactAnalysis analysis) {
        impactItemViewRepository.deleteByTenantIdAndMaintenanceIdAndAnalysisId(
                tenantId, maintenanceId, analysis.analysisId());
        List<MaintenanceRetroactiveImpactItemView> views = analysis.items().stream()
                .map(item -> toView(maintenanceId, tenantId, analysis, item))
                .toList();
        impactItemViewRepository.saveAll(views);
    }

    private MaintenanceRetroactiveImpactItemView toView(
            String maintenanceId,
            String tenantId,
            MaintenanceRetroactiveImpactAnalysis analysis,
            MaintenanceRetroactiveImpactItem item) {
        MaintenanceRetroactiveImpactItemView view = new MaintenanceRetroactiveImpactItemView();
        view.setImpactRecordId(analysis.analysisId() + '|' + item.itemId());
        view.setMaintenanceId(maintenanceId);
        view.setAnalysisId(analysis.analysisId());
        view.setAnalysisVersion(analysis.analysisVersion());
        view.setItemId(item.itemId());
        view.setSourceDomain(item.sourceDomain());
        view.setImpactType(item.impactType());
        view.setReferenceId(item.referenceId());
        view.setReferenceNumber(item.referenceNumber());
        view.setOccurredAt(item.occurredAt());
        view.setSourceStatus(item.sourceStatus());
        view.setAmount(item.amount());
        view.setCurrency(item.currency());
        view.setSeverity(item.severity());
        view.setHandlingStatus(item.handlingStatus());
        view.setSummary(item.summary());
        view.setEvidenceVersion(item.evidenceVersion());
        view.setEvidenceHash(item.evidenceHash());
        view.setTenantId(tenantId);
        view.setCreateTime(analysis.completedAt());
        view.setUpdateTime(analysis.completedAt());
        return view;
    }
}
