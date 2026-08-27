package com.titanium.maintenance.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.alibaba.fastjson2.JSON;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.query.query.MaintenanceCaseSearchCriteria;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactiveImpactItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCasePageQueryResult;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceRetroactiveImpactItemView;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceSnapshotView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.valueobject.workflow.MaintenanceAppliedFieldEvidence;

class MaintenanceCaseQueryServiceImplTest {

    private MaintenanceViewRepository maintenanceViewRepository;
    private MaintenanceCaseItemViewRepository itemViewRepository;
    private MaintenanceFieldChangeViewRepository fieldViewRepository;
    private MaintenanceSnapshotViewRepository snapshotViewRepository;
    private MaintenanceWorkflowTaskViewRepository workflowTaskViewRepository;
    private MaintenanceRetroactiveImpactItemViewRepository retroactiveImpactItemViewRepository;
    private MaintenanceRetroactivePeriodAdjustmentViewRepository retroactivePeriodAdjustmentViewRepository;
    private MaintenanceCaseQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        maintenanceViewRepository = mock(MaintenanceViewRepository.class);
        itemViewRepository = mock(MaintenanceCaseItemViewRepository.class);
        fieldViewRepository = mock(MaintenanceFieldChangeViewRepository.class);
        snapshotViewRepository = mock(MaintenanceSnapshotViewRepository.class);
        workflowTaskViewRepository = mock(MaintenanceWorkflowTaskViewRepository.class);
        retroactiveImpactItemViewRepository = mock(MaintenanceRetroactiveImpactItemViewRepository.class);
        retroactivePeriodAdjustmentViewRepository = mock(MaintenanceRetroactivePeriodAdjustmentViewRepository.class);
        service = new MaintenanceCaseQueryServiceImpl(
                maintenanceViewRepository, itemViewRepository, fieldViewRepository, snapshotViewRepository,
                workflowTaskViewRepository, retroactiveImpactItemViewRepository,
                retroactivePeriodAdjustmentViewRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPageOnlyThroughDatabaseSpecificationAndAttachItemCodes() {
        MaintenanceView view = mainView();
        when(maintenanceViewRepository.findAll(
                any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(view)));
        MaintenanceCaseItemView item = itemView();
        when(itemViewRepository.findByTenantIdAndMaintenanceIdInOrderByMaintenanceIdAscItemCodeAsc(
                "tenant-1", List.of("case-1")))
                .thenReturn(List.of(item));
        MaintenanceCaseSearchCriteria criteria = new MaintenanceCaseSearchCriteria(
                null, "P202608240001", null, "POLICY_INFO_CHANGE",
                MaintenanceChannel.MANUAL, MaintenanceStatus.PENDING, null,
                null, null, 0, 20);

        MaintenanceCasePageQueryResult result = service.search("tenant-1", criteria);

        assertEquals(1, result.total());
        assertEquals(List.of("POLICY_INFO_CHANGE"), result.list().getFirst().itemCodes());
        verify(maintenanceViewRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldLoadDetailOnlyThroughVisibleIndependentCaseLookup() {
        MaintenanceView view = mainView();
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "case-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        when(itemViewRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAsc("tenant-1", "case-1"))
                .thenReturn(List.of(itemView()));
        when(fieldViewRepository.findByTenantIdAndMaintenanceIdOrderByItemCodeAscFieldCodeAscObjectIdAsc(
                "tenant-1", "case-1"))
                .thenReturn(List.of());
        when(snapshotViewRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(new MaintenanceSnapshotView()));
        when(workflowTaskViewRepository.findByTenantIdAndMaintenanceIdOrderByItemOrderAscSequenceAsc(
                "tenant-1", "case-1"))
                .thenReturn(List.of(workflowTaskView()));
        when(retroactiveImpactItemViewRepository.findByTenantIdAndMaintenanceIdAndAnalysisId(
                "tenant-1", "case-1", "analysis-1"))
                .thenReturn(List.of(retroactiveImpactItemView()));
        when(retroactivePeriodAdjustmentViewRepository
                .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                        "tenant-1", "case-1", "period-recalculation-1"))
                .thenReturn(List.of(retroactivePeriodAdjustmentView()));

        Optional<MaintenanceCaseDetailQueryResult> result = service.findDetail("tenant-1", "case-1");

        assertTrue(result.isPresent());
        assertEquals("P202608240001", result.orElseThrow().policyNumber());
        assertEquals("configuration-1", result.orElseThrow().items().getFirst().configurationId());
        assertEquals(MaintenanceWorkflowTaskStatus.READY,
                result.orElseThrow().workflowTasks().getFirst().status());
        assertEquals("operator-1",
                result.orElseThrow().workflowTasks().getFirst().assignment().assignee());
        assertEquals(1, result.orElseThrow().workflowTasks().getFirst().retryCount());
        assertEquals("posting-1",
                result.orElseThrow().workflowTasks().getFirst().billingPostingEvidence().postingId());
        assertEquals(MaintenanceBillingPostingStatus.POSTED,
                result.orElseThrow().workflowTasks().getFirst().billingPostingEvidence().status());
        assertEquals("payment-1",
                result.orElseThrow().workflowTasks().getFirst().fundSettlementEvidence().orderId());
        assertEquals(MaintenanceFundSettlementStatus.SUCCEEDED,
                result.orElseThrow().workflowTasks().getFirst().fundSettlementEvidence().status());
        assertEquals(MaintenanceEffectStatus.APPLIED, result.orElseThrow().effectStatus());
        assertEquals(false, result.orElseThrow().effectCompensation().required());
        assertEquals("compensation-1", result.orElseThrow().effectCompensation().compensationId());
        assertEquals("effect-request-1", result.orElseThrow().effectCompensation().requestId());
        assertEquals("END-20260825-001", result.orElseThrow().effectCompensation().endorsementNo());
        assertEquals("operator-2", result.orElseThrow().effectCompensation().resolvedBy());
        assertEquals("END-20260825-001",
                result.orElseThrow().workflowTasks().getFirst()
                        .effectEvidence().application().endorsementNo());
        assertEquals("13900000000",
                result.orElseThrow().workflowTasks().getFirst()
                        .effectEvidence().application().appliedFields().getFirst().canonicalValue());
        assertEquals(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED,
                result.orElseThrow().retroactiveImpactAnalysis().status());
        assertEquals("END-001", result.orElseThrow().retroactiveImpactAnalysis()
                .items().getFirst().referenceNumber());
        assertEquals(MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED,
                result.orElseThrow().retroactivePeriodRecalculation().status());
        assertEquals(new BigDecimal("100.00"), result.orElseThrow().retroactivePeriodRecalculation()
                .periods().getFirst().originalAmount());
        assertEquals("CLOSED_PERIOD_REVIEW", result.orElseThrow().retroactivePeriodRecalculation()
                .periods().getFirst().billingStatus());
    }

    @Test
    void shouldReturnEmptyForAnotherTenantOrIncompleteCase() {
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "case-1", "tenant-2"))
                .thenReturn(Optional.empty());

        assertTrue(service.findDetail("tenant-2", "case-1").isEmpty());
    }

    private MaintenanceView mainView() {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("case-1");
        view.setPolicyId("policy-1");
        view.setPolicyNumber("P202608240001");
        view.setCustomerId("customer-1");
        view.setProductId("product-1");
        view.setProductVersion("product-v1");
        view.setPlanVersion("plan-v1");
        view.setPolicyBaselineVersion(7L);
        view.setSource(MaintenanceChannel.MANUAL);
        view.setStatus(MaintenanceStatus.PENDING);
        view.setEffectStatus(MaintenanceEffectStatus.APPLIED);
        view.setEffectCompensationRequired(false);
        view.setEffectCompensationId("compensation-1");
        view.setEffectCompensationRequestId("effect-request-1");
        view.setEffectCompensationEndorsementNo("END-20260825-001");
        view.setEffectCompensationPolicyVersion(8L);
        view.setEffectCompensationApplicationHash("f".repeat(64));
        view.setEffectCompensationReason("案件回执写入失败");
        view.setEffectCompensationRecordedAt(LocalDateTime.parse("2026-08-25T16:00:00"));
        view.setEffectCompensationResolvedAt(LocalDateTime.parse("2026-08-25T16:05:00"));
        view.setEffectCompensationResolvedBy("operator-2");
        view.setRetroactiveImpactAnalysisId("analysis-1");
        view.setRetroactiveImpactAnalysisVersion(1);
        view.setRetroactiveImpactOperationId("operation-1");
        view.setRetroactiveImpactRequestHash("a".repeat(64));
        view.setRetroactiveImpactScopeFrom(LocalDateTime.parse("2026-08-01T00:00:00"));
        view.setRetroactiveImpactScopeTo(LocalDateTime.parse("2026-08-25T18:00:00"));
        view.setRetroactiveImpactStatus(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED);
        view.setRetroactiveImpactCoveredDomains("BILLING,CLAIM,PAYMENT,POLICY");
        view.setRetroactiveImpactItemCount(1);
        view.setRetroactiveImpactBlockingCount(1);
        view.setRetroactiveImpactPendingCount(1);
        view.setRetroactiveImpactEvidenceVersion("evidence-v1");
        view.setRetroactiveImpactResultHash("b".repeat(64));
        view.setRetroactiveImpactStartedAt(LocalDateTime.parse("2026-08-25T18:00:00"));
        view.setRetroactiveImpactCompletedAt(LocalDateTime.parse("2026-08-25T18:01:00"));
        view.setRetroactiveImpactUpdatedAt(LocalDateTime.parse("2026-08-25T18:01:00"));
        view.setRetroactivePeriodRecalculationId("period-recalculation-1");
        view.setRetroactivePeriodRecalculationVersion(1);
        view.setRetroactivePeriodRecalculationOperationId("period-operation-1");
        view.setRetroactivePeriodRecalculationRequestHash("c".repeat(64));
        view.setRetroactivePeriodAnalysisId("analysis-1");
        view.setRetroactivePeriodAnalysisVersion(1);
        view.setRetroactivePeriodAnalysisResultHash("b".repeat(64));
        view.setRetroactivePeriodRecalculationStatus(
                MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED);
        view.setRetroactiveProductRecalculationId("product-recalculation-1");
        view.setRetroactiveProductRecalculationVersion("PERIOD_V1");
        view.setRetroactiveProductOriginalCalculationId("calc-original");
        view.setRetroactiveProductOriginalResultHash("d".repeat(64));
        view.setRetroactiveProductReplacementCalculationId("calc-replacement");
        view.setRetroactiveProductReplacementResultHash("e".repeat(64));
        view.setRetroactiveProductDirection(MaintenanceBalanceDirection.DEBIT);
        view.setRetroactiveProductAmount(new BigDecimal("20.00"));
        view.setRetroactiveProductCurrency("CNY");
        view.setRetroactiveProductInputHash("f".repeat(64));
        view.setRetroactiveProductResultHash("a".repeat(64));
        view.setRetroactiveProductCalculatedAt(LocalDateTime.parse("2026-08-25T18:02:00"));
        view.setRetroactivePeriodCount(1);
        view.setRetroactiveBillingBatchId("billing-batch-1");
        view.setRetroactiveBillingStatus("REVIEW_REQUIRED");
        view.setRetroactiveBillingReviewCount(1);
        view.setRetroactiveBillingRequestHash("b".repeat(64));
        view.setRetroactiveBillingResultHash("c".repeat(64));
        view.setRetroactiveBillingAdjustedAt(LocalDateTime.parse("2026-08-25T18:03:00"));
        view.setRetroactivePeriodStartedAt(LocalDateTime.parse("2026-08-25T18:01:00"));
        view.setRetroactivePeriodCompletedAt(LocalDateTime.parse("2026-08-25T18:03:00"));
        view.setRetroactivePeriodUpdatedAt(LocalDateTime.parse("2026-08-25T18:03:00"));
        view.setCreatedBy("operator-1");
        view.setCreateTime(LocalDateTime.parse("2026-08-24T10:00:00"));
        view.setUpdateTime(LocalDateTime.parse("2026-08-24T10:05:00"));
        view.setTenantId("tenant-1");
        return view;
    }

    private MaintenanceRetroactiveImpactItemView retroactiveImpactItemView() {
        MaintenanceRetroactiveImpactItemView item = new MaintenanceRetroactiveImpactItemView();
        item.setImpactRecordId("analysis-1|POLICY:END-001");
        item.setMaintenanceId("case-1");
        item.setAnalysisId("analysis-1");
        item.setAnalysisVersion(1);
        item.setItemId("POLICY:END-001");
        item.setSourceDomain(MaintenanceRetroactiveImpactDomain.POLICY);
        item.setImpactType(MaintenanceRetroactiveImpactType.SUBSEQUENT_ENDORSEMENT);
        item.setReferenceId("END-001");
        item.setReferenceNumber("END-001");
        item.setOccurredAt(LocalDateTime.parse("2026-08-20T10:00:00"));
        item.setSourceStatus("ENDORSED");
        item.setSeverity(MaintenanceRetroactiveImpactSeverity.BLOCKING);
        item.setHandlingStatus(MaintenanceRetroactiveImpactItemStatus.PENDING);
        item.setSummary("追溯时点后存在已落地批单");
        item.setEvidenceVersion("POLICY_ENDORSEMENT_V1");
        item.setEvidenceHash("c".repeat(64));
        item.setTenantId("tenant-1");
        return item;
    }

    private MaintenanceRetroactivePeriodAdjustmentView retroactivePeriodAdjustmentView() {
        MaintenanceRetroactivePeriodAdjustmentView period = new MaintenanceRetroactivePeriodAdjustmentView();
        period.setPeriodId("BILLING:bill-1");
        period.setSourceReferenceId("bill-1");
        period.setAccountingPeriod("2026-07");
        period.setPeriodStart(LocalDateTime.parse("2026-07-01T00:00:00"));
        period.setOriginalAmount(new BigDecimal("100.00"));
        period.setRecalculatedAmount(new BigDecimal("120.00"));
        period.setDirection(MaintenanceBalanceDirection.DEBIT);
        period.setDifferenceAmount(new BigDecimal("20.00"));
        period.setCurrency("CNY");
        period.setBillingStatus("CLOSED_PERIOD_REVIEW");
        period.setSourceEvidenceHash("d".repeat(64));
        period.setProductResultHash("e".repeat(64));
        period.setBillingResultHash("f".repeat(64));
        return period;
    }

    private MaintenanceCaseItemView itemView() {
        MaintenanceCaseItemView item = new MaintenanceCaseItemView();
        item.setMaintenanceId("case-1");
        item.setItemCode("POLICY_INFO_CHANGE");
        item.setItemName("保单基本信息变更");
        item.setItemCategory("BASIC_INFORMATION");
        item.setConfigurationId("configuration-1");
        item.setConfigurationVersion("1.0.0");
        item.setSelectedAt(LocalDateTime.parse("2026-08-24T10:01:00"));
        return item;
    }

    private MaintenanceWorkflowTaskView workflowTaskView() {
        MaintenanceWorkflowTaskView task = new MaintenanceWorkflowTaskView();
        task.setTaskId("case-1:POLICY_INFO_CHANGE:DATA_ENTRY");
        task.setMaintenanceId("case-1");
        task.setItemCode("POLICY_INFO_CHANGE");
        task.setItemOrder(0);
        task.setSequence(2);
        task.setStepType(MaintenanceStepType.DATA_ENTRY);
        task.setMode(MaintenanceStepMode.REQUIRED);
        task.setStatus(MaintenanceWorkflowTaskStatus.READY);
        task.setAssignedTo("operator-1");
        task.setClaimedAt(LocalDateTime.parse("2026-08-24T10:04:00"));
        task.setRetryCount(1);
        task.setBillingPostingId("posting-1");
        task.setBillingAdjustmentId("quote-1");
        task.setBillingResultHash("a".repeat(64));
        task.setBillingPostingDirection(MaintenanceBalanceDirection.DEBIT);
        task.setBillingPostingAmount(new BigDecimal("20"));
        task.setBillingPostingCurrency("CNY");
        task.setBillingPostingStatus(MaintenanceBillingPostingStatus.POSTED);
        task.setBillingCommissionAdjustmentCount(1);
        task.setBillingPostedAt(LocalDateTime.parse("2026-08-24T10:05:00"));
        task.setFundSettlementType(MaintenanceFundSettlementType.COLLECTION);
        task.setFundSettlementStatus(MaintenanceFundSettlementStatus.SUCCEEDED);
        task.setFundSourcePostingId("posting-1");
        task.setFundSettlementOrderId("payment-1");
        task.setFundSettlementExternalStatus("SUCCESS");
        task.setFundSettlementAmount(new BigDecimal("20"));
        task.setFundSettlementCurrency("CNY");
        task.setFundSettlementRecordedAt(LocalDateTime.parse("2026-08-24T10:06:00"));
        task.setEffectRequestId("effect-request-1");
        task.setEffectRequestHash("b".repeat(64));
        task.setEffectExpectedPolicyVersion(7L);
        task.setEffectTimeType(EffectiveTimeType.IMMEDIATE);
        task.setEffectRequestedEffectiveAt(LocalDateTime.parse("2026-08-24T10:07:00"));
        task.setEffectProposedSnapshotHash("c".repeat(64));
        task.setEffectRequestedAt(LocalDateTime.parse("2026-08-24T10:07:00"));
        task.setPolicyEndorsementNo("END-20260825-001");
        task.setPolicyActualVersion(8L);
        task.setPolicyApplicationHash("d".repeat(64));
        task.setAppliedSnapshotStorageKey("snapshot://case-1/applied");
        task.setAppliedSnapshotHash("e".repeat(64));
        task.setAppliedSnapshotPolicyVersion(8L);
        task.setAppliedSnapshotCapturedAt("2026-08-24T10:08:00+08:00");
        task.setAppliedFieldsJson(JSON.toJSONString(List.of(new MaintenanceAppliedFieldEvidence(
                "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                PolicyFieldDataType.TEXT, "13900000000"))));
        task.setPolicyAppliedAt(LocalDateTime.parse("2026-08-24T10:08:00"));
        task.setTenantId("tenant-1");
        return task;
    }
}
