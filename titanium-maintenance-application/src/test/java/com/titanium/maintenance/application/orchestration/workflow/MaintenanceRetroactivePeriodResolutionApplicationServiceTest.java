package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.MaintenanceRetroactivePeriodResolutionInput;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.port.BillingRetroactivePeriodResolutionPort;
import com.titanium.maintenance.port.BillingRetroactivePeriodResolutionPort.ResolutionFact;
import com.titanium.maintenance.port.BillingRetroactivePeriodResolutionPort.ResolutionLineFact;
import com.titanium.maintenance.port.BillingRetroactivePeriodResolutionPort.ResolutionRequest;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;

class MaintenanceRetroactivePeriodResolutionApplicationServiceTest {

    private static final LocalDateTime RESOLVED_AT = LocalDateTime.of(2026, 8, 26, 14, 0);

    private CommandGateway commandGateway;
    private MaintenanceViewRepository repository;
    private BillingRetroactivePeriodResolutionPort billingPort;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        repository = mock(MaintenanceViewRepository.class);
        billingPort = mock(BillingRetroactivePeriodResolutionPort.class);
        when(repository.findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                "case-1", "tenant-1")).thenReturn(Optional.of(readyView()));
        when(billingPort.resolve(any())).thenAnswer(invocation ->
                fact(invocation.<ResolutionRequest>getArgument(0).resolutionRequestId()));
    }

    @Test
    void shouldFreezeRequestAndRecordBillingResolution() {
        var result = service().resolve(input());

        assertEquals(MaintenanceRetroactivePeriodResolutionStatus.COMPLETED, result.status());
        assertEquals("2026-08", result.targetAccountingPeriod());
        assertEquals("posting-1", fact("request-1").lines().getFirst().postingReference());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(captor.capture());
        assertEquals(StartMaintenanceRetroactivePeriodResolutionCommand.class,
                captor.getAllValues().getFirst().getClass());
        assertEquals(CompleteMaintenanceRetroactivePeriodResolutionCommand.class,
                captor.getAllValues().get(1).getClass());
    }

    @Test
    void shouldReturnExistingCompletedResultForSameOperation() {
        MaintenanceView view = readyView();
        view.setRetroactivePeriodResolutionId("resolution-1");
        view.setRetroactivePeriodResolutionOperationId("operation-1");
        view.setRetroactivePeriodResolutionStatus(MaintenanceRetroactivePeriodResolutionStatus.COMPLETED);
        view.setRetroactiveBillingResolutionId("billing-resolution-1");
        view.setRetroactivePeriodResolutionSourceBatchHash(hash('b'));
        view.setRetroactivePeriodResolutionTargetPeriod("2026-08");
        view.setRetroactivePeriodResolutionResolvedLineCount(1);
        view.setRetroactivePeriodResolutionResultHash(hash('r'));
        view.setRetroactivePeriodResolutionReason("结转至当前开放期间");
        view.setRetroactivePeriodResolutionCompletedAt(RESOLVED_AT);
        when(repository.findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                "case-1", "tenant-1")).thenReturn(Optional.of(view));

        var result = service().resolve(input());

        assertEquals("billing-resolution-1", result.billingResolutionId());
        verify(billingPort, never()).resolve(any());
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void shouldRecordFailureWhenBillingResolutionFails() {
        doThrow(new IllegalStateException("Billing unavailable")).when(billingPort).resolve(any());

        var result = service().resolve(input());

        assertEquals(MaintenanceRetroactivePeriodResolutionStatus.FAILED, result.status());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(captor.capture());
        assertEquals(FailMaintenanceRetroactivePeriodResolutionCommand.class,
                captor.getAllValues().get(1).getClass());
    }

    @Test
    void shouldNotRecordFailureWhenCompletionCommandTimesOut() {
        when(commandGateway.sendAndWait(any())).thenAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof CompleteMaintenanceRetroactivePeriodResolutionCommand) {
                throw new IllegalStateException("command timeout");
            }
            return null;
        });

        assertThrows(IllegalStateException.class, () -> service().resolve(input()));

        verify(commandGateway, never()).sendAndWait(any(FailMaintenanceRetroactivePeriodResolutionCommand.class));
    }

    private MaintenanceRetroactivePeriodResolutionApplicationService service() {
        return new MaintenanceRetroactivePeriodResolutionApplicationService(
                commandGateway, repository, billingPort);
    }

    private MaintenanceView readyView() {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("case-1");
        view.setPolicyId("policy-1");
        view.setEffectiveTimeType(EffectiveTimeType.RETROACTIVE);
        view.setRetroactiveImpactStatus(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED);
        view.setRetroactivePeriodRecalculationStatus(MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED);
        view.setRetroactiveBillingBatchId("billing-batch-1");
        view.setRetroactiveBillingReviewCount(1);
        view.setRetroactiveBillingResultHash(hash('b'));
        return view;
    }

    private MaintenanceRetroactivePeriodResolutionInput input() {
        return new MaintenanceRetroactivePeriodResolutionInput(
                "case-1", "operation-1", "2026-08", "结转至当前开放期间",
                "operator-1", "tenant-1");
    }

    private ResolutionFact fact(String requestId) {
        return new ResolutionFact(
                "billing-resolution-1", requestId, "billing-batch-1", "tenant-1",
                "case-1", "policy-1", hash('b'), YearMonth.of(2026, 8), "COMPLETED", 1,
                hash('q'), hash('r'), "结转至当前开放期间", "operator-1", RESOLVED_AT,
                List.of(new ResolutionLineFact(
                        "BILLING:bill-1", YearMonth.of(2026, 7), YearMonth.of(2026, 8),
                        MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"), "CNY",
                        "posting-1", hash('z'), hash('l'))));
    }

    private String hash(char value) {
        return String.valueOf((char) ('a' + Math.floorMod(value, 6))).repeat(64);
    }
}
