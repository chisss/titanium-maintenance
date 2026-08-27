package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationFailedEvent;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveBillingAdjustmentEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveBillingPeriodAdjustment;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodRecalculation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductPeriodDifference;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductRecalculationEvidence;

class MaintenanceRetroactivePeriodRecalculationProjectionEventHandlerTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 26, 10, 0);

    @Test
    void shouldProjectProductBeforeAfterAndClosedPeriodReview() {
        MaintenanceViewRepository caseRepository = mock(MaintenanceViewRepository.class);
        MaintenanceRetroactivePeriodAdjustmentViewRepository periodRepository =
                mock(MaintenanceRetroactivePeriodAdjustmentViewRepository.class);
        MaintenanceView view = new MaintenanceView();
        when(caseRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        MaintenanceRetroactivePeriodRecalculationProjectionEventHandler handler =
                new MaintenanceRetroactivePeriodRecalculationProjectionEventHandler(
                        caseRepository, periodRepository);
        MaintenanceRetroactivePeriodRecalculation recalculation = recalculation()
                .recordProduct(productEvidence(), STARTED_AT.plusMinutes(1))
                .completeBilling(billingEvidence(), STARTED_AT.plusMinutes(2));

        handler.on(new MaintenanceRetroactivePeriodRecalculationCompletedEvent(
                MaintenanceId.of("case-1"), recalculation, "operator-1", "tenant-1"));

        assertEquals(MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED,
                view.getRetroactivePeriodRecalculationStatus());
        assertEquals("billing-batch-1", view.getRetroactiveBillingBatchId());
        assertEquals(1, view.getRetroactiveBillingReviewCount());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<MaintenanceRetroactivePeriodAdjustmentView>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(periodRepository).saveAll(captor.capture());
        MaintenanceRetroactivePeriodAdjustmentView period = captor.getValue().iterator().next();
        assertEquals(new BigDecimal("100.00"), period.getOriginalAmount());
        assertEquals(new BigDecimal("120.00"), period.getRecalculatedAmount());
        assertEquals("2026-07", period.getAccountingPeriod());
        assertEquals("CLOSED_PERIOD_REVIEW", period.getBillingStatus());
    }

    @Test
    void shouldKeepProductRowsWhenBillingFailureIsProjected() {
        MaintenanceViewRepository caseRepository = mock(MaintenanceViewRepository.class);
        MaintenanceRetroactivePeriodAdjustmentViewRepository periodRepository =
                mock(MaintenanceRetroactivePeriodAdjustmentViewRepository.class);
        MaintenanceView view = new MaintenanceView();
        when(caseRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        MaintenanceRetroactivePeriodRecalculationProjectionEventHandler handler =
                new MaintenanceRetroactivePeriodRecalculationProjectionEventHandler(
                        caseRepository, periodRepository);
        MaintenanceRetroactivePeriodRecalculation failed = recalculation()
                .recordProduct(productEvidence(), STARTED_AT.plusMinutes(1))
                .fail("BILLING_FAILED", "账务服务不可用", STARTED_AT.plusMinutes(2));

        handler.on(new MaintenanceRetroactivePeriodRecalculationFailedEvent(
                MaintenanceId.of("case-1"), failed, "operator-1", "tenant-1"));

        assertEquals(MaintenanceRetroactivePeriodRecalculationStatus.FAILED,
                view.getRetroactivePeriodRecalculationStatus());
        assertEquals("product-recalculation-1", view.getRetroactiveProductRecalculationId());
        verify(periodRepository, never()).deleteByTenantIdAndMaintenanceId(any(), any());
        verify(periodRepository, never()).saveAll(any());
    }

    private MaintenanceRetroactivePeriodRecalculation recalculation() {
        return MaintenanceRetroactivePeriodRecalculation.start(
                "period-recalculation-1", 1, "operation-1", hash('q'),
                "analysis-1", 1, hash('a'), STARTED_AT);
    }

    private MaintenanceRetroactiveProductRecalculationEvidence productEvidence() {
        return new MaintenanceRetroactiveProductRecalculationEvidence(
                "product-recalculation-1", "PERIOD_V1", "calc-original", hash('o'),
                "calc-replacement", hash('n'), MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20.00"), "CNY", hash('i'), hash('r'), STARTED_AT.plusMinutes(1),
                List.of(productPeriod()));
    }

    private MaintenanceRetroactiveProductPeriodDifference productPeriod() {
        return new MaintenanceRetroactiveProductPeriodDifference(
                "BILLING:bill-1", "bill-1", LocalDateTime.of(2026, 7, 1, 0, 0),
                new BigDecimal("100.00"), new BigDecimal("120.00"),
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"), "CNY",
                hash('s'), hash('p'));
    }

    private MaintenanceRetroactiveBillingAdjustmentEvidence billingEvidence() {
        return new MaintenanceRetroactiveBillingAdjustmentEvidence(
                "billing-batch-1", "REVIEW_REQUIRED", 0, 1, hash('b'), hash('c'),
                STARTED_AT.plusMinutes(2), List.of(new MaintenanceRetroactiveBillingPeriodAdjustment(
                        "BILLING:bill-1", "bill-1", "2026-07", LocalDateTime.of(2026, 7, 1, 0, 0),
                        new BigDecimal("100.00"), new BigDecimal("120.00"),
                        MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"), "CNY",
                        "CLOSED_PERIOD_REVIEW", hash('s'), hash('p'), hash('z'))));
    }

    private String hash(char value) {
        return String.valueOf((char) ('a' + Math.floorMod(value, 6))).repeat(64);
    }
}
