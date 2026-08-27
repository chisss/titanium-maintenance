package com.titanium.maintenance.query.handler.projection;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationFailedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationStartedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveProductRecalculationRecordedEvent;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveBillingPeriodAdjustment;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodRecalculation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductPeriodDifference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 投影追溯期间重算摘要以及 Product/Billing 逐期间核对结果。 */
@Slf4j
@Component
@ProcessingGroup("maintenance-query-group")
@RequiredArgsConstructor
public class MaintenanceRetroactivePeriodRecalculationProjectionEventHandler {

    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceRetroactivePeriodAdjustmentViewRepository periodAdjustmentViewRepository;

    @EventHandler
    @Transactional
    public void on(MaintenanceRetroactivePeriodRecalculationStartedEvent event) {
        periodAdjustmentViewRepository.deleteByTenantIdAndMaintenanceId(
                event.tenantId(), event.maintenanceId().id());
        updateCase(event.maintenanceId().id(), event.tenantId(), event.recalculation(), event.startedBy());
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceRetroactiveProductRecalculationRecordedEvent event) {
        updateCase(event.maintenanceId().id(), event.tenantId(), event.recalculation(), event.recordedBy());
        replacePeriods(event.maintenanceId().id(), event.tenantId(), event.recalculation());
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceRetroactivePeriodRecalculationCompletedEvent event) {
        updateCase(event.maintenanceId().id(), event.tenantId(), event.recalculation(), event.completedBy());
        replacePeriods(event.maintenanceId().id(), event.tenantId(), event.recalculation());
    }

    @EventHandler
    @Transactional
    public void on(MaintenanceRetroactivePeriodRecalculationFailedEvent event) {
        updateCase(event.maintenanceId().id(), event.tenantId(), event.recalculation(), event.failedBy());
    }

    private void updateCase(
            String maintenanceId,
            String tenantId,
            MaintenanceRetroactivePeriodRecalculation recalculation,
            String operatorId) {
        maintenanceViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .ifPresentOrElse(view -> {
                    apply(view, recalculation, operatorId);
                    maintenanceViewRepository.save(view);
                }, () -> log.warn("[追溯期间重算投影] 未找到案件 maintenanceId={}", maintenanceId));
    }

    private void apply(
            MaintenanceView view,
            MaintenanceRetroactivePeriodRecalculation recalculation,
            String operatorId) {
        view.setRetroactivePeriodRecalculationId(recalculation.periodRecalculationId());
        view.setRetroactivePeriodRecalculationVersion(recalculation.periodRecalculationVersion());
        view.setRetroactivePeriodRecalculationOperationId(recalculation.operationId());
        view.setRetroactivePeriodRecalculationRequestHash(recalculation.requestHash());
        view.setRetroactivePeriodAnalysisId(recalculation.analysisId());
        view.setRetroactivePeriodAnalysisVersion(recalculation.analysisVersion());
        view.setRetroactivePeriodAnalysisResultHash(recalculation.analysisResultHash());
        view.setRetroactivePeriodRecalculationStatus(recalculation.status());
        applyProduct(view, recalculation);
        applyBilling(view, recalculation);
        view.setRetroactivePeriodFailureCode(recalculation.failureCode());
        view.setRetroactivePeriodFailureMessage(recalculation.failureMessage());
        view.setRetroactivePeriodStartedAt(recalculation.startedAt());
        view.setRetroactivePeriodCompletedAt(recalculation.completedAt());
        view.setRetroactivePeriodUpdatedAt(recalculation.updatedAt());
        view.setUpdatedBy(operatorId);
        view.setUpdateTime(recalculation.updatedAt());
    }

    private void applyProduct(
            MaintenanceView view,
            MaintenanceRetroactivePeriodRecalculation recalculation) {
        var product = recalculation.productEvidence();
        view.setRetroactiveProductRecalculationId(product == null ? null : product.recalculationId());
        view.setRetroactiveProductRecalculationVersion(product == null ? null : product.recalculationVersion());
        view.setRetroactiveProductOriginalCalculationId(product == null ? null : product.originalCalculationId());
        view.setRetroactiveProductOriginalResultHash(product == null ? null : product.originalResultHash());
        view.setRetroactiveProductReplacementCalculationId(
                product == null ? null : product.replacementCalculationId());
        view.setRetroactiveProductReplacementResultHash(
                product == null ? null : product.replacementResultHash());
        view.setRetroactiveProductDirection(product == null ? null : product.direction());
        view.setRetroactiveProductAmount(product == null ? null : product.amount());
        view.setRetroactiveProductCurrency(product == null ? null : product.currency());
        view.setRetroactiveProductInputHash(product == null ? null : product.inputHash());
        view.setRetroactiveProductResultHash(product == null ? null : product.resultHash());
        view.setRetroactiveProductCalculatedAt(product == null ? null : product.calculatedAt());
        view.setRetroactivePeriodCount(product == null ? 0 : product.periods().size());
    }

    private void applyBilling(
            MaintenanceView view,
            MaintenanceRetroactivePeriodRecalculation recalculation) {
        var billing = recalculation.billingEvidence();
        view.setRetroactiveBillingBatchId(billing == null ? null : billing.batchId());
        view.setRetroactiveBillingStatus(billing == null ? null : billing.status());
        view.setRetroactiveBillingPostedCount(billing == null ? 0 : billing.postedCount());
        view.setRetroactiveBillingReviewCount(billing == null ? 0 : billing.reviewCount());
        view.setRetroactiveBillingRequestHash(billing == null ? null : billing.requestHash());
        view.setRetroactiveBillingResultHash(billing == null ? null : billing.resultHash());
        view.setRetroactiveBillingAdjustedAt(billing == null ? null : billing.adjustedAt());
    }

    private void replacePeriods(
            String maintenanceId,
            String tenantId,
            MaintenanceRetroactivePeriodRecalculation recalculation) {
        periodAdjustmentViewRepository.deleteByTenantIdAndMaintenanceId(tenantId, maintenanceId);
        if (recalculation.productEvidence() == null) {
            return;
        }
        Map<String, MaintenanceRetroactiveBillingPeriodAdjustment> billingPeriods =
                recalculation.billingEvidence() == null
                        ? Map.of()
                        : recalculation.billingEvidence().periods().stream()
                                .collect(Collectors.toMap(
                                        MaintenanceRetroactiveBillingPeriodAdjustment::periodId,
                                        Function.identity()));
        periodAdjustmentViewRepository.saveAll(recalculation.productEvidence().periods().stream()
                .map(product -> toView(
                        maintenanceId, tenantId, recalculation, product,
                        billingPeriods.get(product.periodId())))
                .toList());
    }

    private MaintenanceRetroactivePeriodAdjustmentView toView(
            String maintenanceId,
            String tenantId,
            MaintenanceRetroactivePeriodRecalculation recalculation,
            MaintenanceRetroactiveProductPeriodDifference product,
            MaintenanceRetroactiveBillingPeriodAdjustment billing) {
        MaintenanceRetroactivePeriodAdjustmentView view = new MaintenanceRetroactivePeriodAdjustmentView();
        view.setPeriodRecordId(recalculation.periodRecalculationId() + '|' + product.periodId());
        view.setMaintenanceId(maintenanceId);
        view.setPeriodRecalculationId(recalculation.periodRecalculationId());
        view.setPeriodRecalculationVersion(recalculation.periodRecalculationVersion());
        view.setAnalysisId(recalculation.analysisId());
        view.setAnalysisVersion(recalculation.analysisVersion());
        view.setPeriodId(product.periodId());
        view.setSourceReferenceId(product.sourceReferenceId());
        view.setAccountingPeriod(billing == null ? null : billing.accountingPeriod());
        view.setPeriodStart(product.periodStart());
        view.setOriginalAmount(product.originalAmount());
        view.setRecalculatedAmount(product.recalculatedAmount());
        view.setDirection(product.direction());
        view.setDifferenceAmount(product.differenceAmount());
        view.setCurrency(product.currency());
        view.setBillingStatus(billing == null ? null : billing.status());
        view.setSourceEvidenceHash(product.sourceEvidenceHash());
        view.setProductResultHash(product.resultHash());
        view.setBillingResultHash(billing == null ? null : billing.billingResultHash());
        view.setTenantId(tenantId);
        view.setCreateTime(recalculation.updatedAt());
        view.setUpdateTime(recalculation.updatedAt());
        return view;
    }
}
