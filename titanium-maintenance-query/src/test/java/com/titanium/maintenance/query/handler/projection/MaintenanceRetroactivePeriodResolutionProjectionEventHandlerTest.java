package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionCompletedEvent;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolutionEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolutionLine;

class MaintenanceRetroactivePeriodResolutionProjectionEventHandlerTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 26, 13, 0);

    @Test
    void shouldProjectPostingReferenceToExistingPeriodRow() {
        MaintenanceViewRepository caseRepository = mock(MaintenanceViewRepository.class);
        MaintenanceRetroactivePeriodAdjustmentViewRepository periodRepository =
                mock(MaintenanceRetroactivePeriodAdjustmentViewRepository.class);
        MaintenanceView caseView = new MaintenanceView();
        caseView.setRetroactivePeriodRecalculationId("period-recalculation-1");
        MaintenanceRetroactivePeriodAdjustmentView period = new MaintenanceRetroactivePeriodAdjustmentView();
        period.setPeriodId("BILLING:bill-1");
        period.setBillingResultHash(hash('z'));
        when(caseRepository.findByMaintenanceIdAndTenantId("case-1", "tenant-1"))
                .thenReturn(Optional.of(caseView));
        when(periodRepository
                .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                        "tenant-1", "case-1", "period-recalculation-1"))
                .thenReturn(List.of(period));
        var handler = new MaintenanceRetroactivePeriodResolutionProjectionEventHandler(
                caseRepository, periodRepository);
        MaintenanceRetroactivePeriodResolution resolution = MaintenanceRetroactivePeriodResolution.start(
                "resolution-1", "operation-1", hash('q'), "billing-batch-1", hash('b'),
                "2026-08", "结转至当前开放期间", STARTED_AT)
                .complete(evidence(), STARTED_AT.plusMinutes(1));

        handler.on(new MaintenanceRetroactivePeriodResolutionCompletedEvent(
                MaintenanceId.of("case-1"), resolution, "operator-1", "tenant-1"));

        assertEquals(MaintenanceRetroactivePeriodResolutionStatus.COMPLETED,
                caseView.getRetroactivePeriodResolutionStatus());
        assertEquals("posting-1", period.getPostingReference());
        assertEquals("2026-08", period.getTargetAccountingPeriod());
        verify(periodRepository).saveAll(List.of(period));
    }

    private MaintenanceRetroactivePeriodResolutionEvidence evidence() {
        return new MaintenanceRetroactivePeriodResolutionEvidence(
                "billing-resolution-1", "mrr-request-1", "billing-batch-1", hash('b'),
                "2026-08", 1, hash('q'), hash('r'), "结转至当前开放期间", "operator-1",
                STARTED_AT.plusMinutes(1), List.of(new MaintenanceRetroactivePeriodResolutionLine(
                        "BILLING:bill-1", "2026-07", "2026-08", MaintenanceBalanceDirection.DEBIT,
                        new BigDecimal("20.00"), "CNY", "posting-1", hash('z'), hash('l'))));
    }

    private String hash(char value) {
        return String.valueOf((char) ('a' + Math.floorMod(value, 6))).repeat(64);
    }
}
