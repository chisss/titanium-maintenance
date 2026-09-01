package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactivePeriodRecalculationInput;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.command.RecordMaintenanceRetroactiveProductRecalculationCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactivePeriodRecalculationCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodAdjustmentPort;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodAdjustmentPort.AdjustmentFact;
import com.titanium.maintenance.port.product.ProductRetroactivePeriodRecalculationPort;
import com.titanium.maintenance.port.product.ProductRetroactivePeriodRecalculationPort.RecalculationFact;
import com.titanium.maintenance.query.repository.MaintenanceRetroactiveImpactItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceRetroactivePeriodAdjustmentViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceRetroactiveImpactItemView;
import com.titanium.maintenance.query.view.MaintenanceRetroactivePeriodAdjustmentView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveBillingPeriodAdjustment;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductPeriodDifference;

class MaintenanceRetroactivePeriodRecalculationApplicationServiceTest {

    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 7, 1, 0, 0);

    private CommandGateway commandGateway;
    private MaintenanceViewRepository caseRepository;
    private MaintenanceRetroactiveImpactItemViewRepository impactRepository;
    private MaintenanceRetroactivePeriodAdjustmentViewRepository periodRepository;
    private ProductRetroactivePeriodRecalculationPort productPort;
    private BillingRetroactivePeriodAdjustmentPort billingPort;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        caseRepository = mock(MaintenanceViewRepository.class);
        impactRepository = mock(MaintenanceRetroactiveImpactItemViewRepository.class);
        periodRepository = mock(MaintenanceRetroactivePeriodAdjustmentViewRepository.class);
        productPort = mock(ProductRetroactivePeriodRecalculationPort.class);
        billingPort = mock(BillingRetroactivePeriodAdjustmentPort.class);
        when(caseRepository.findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                "case-1", "tenant-1")).thenReturn(Optional.of(readyView()));
        when(impactRepository.findByTenantIdAndMaintenanceIdAndAnalysisId(
                "tenant-1", "case-1", "analysis-1")).thenReturn(List.of(impactItem()));
        when(productPort.recalculate(any())).thenReturn(productFact());
        when(billingPort.adjust(any())).thenReturn(billingFact());
    }

    @Test
    void shouldSaveIndependentProductCheckpointBeforeCompletingBilling() {
        var result = service().recalculate(input());

        assertEquals(MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED, result.status());
        assertEquals(new BigDecimal("20.00"), result.amount());
        assertEquals(1, result.postedCount());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(3)).sendAndWait(captor.capture());
        assertEquals(StartMaintenanceRetroactivePeriodRecalculationCommand.class,
                captor.getAllValues().getFirst().getClass());
        assertEquals(RecordMaintenanceRetroactiveProductRecalculationCommand.class,
                captor.getAllValues().get(1).getClass());
        assertEquals(CompleteMaintenanceRetroactivePeriodRecalculationCommand.class,
                captor.getAllValues().get(2).getClass());
    }

    @Test
    void shouldReuseProductCheckpointWhenRetryingBillingFailure() {
        MaintenanceView view = readyView();
        view.setRetroactivePeriodRecalculationId("period-recalculation-1");
        view.setRetroactivePeriodRecalculationVersion(1);
        view.setRetroactivePeriodRecalculationOperationId("operation-1");
        view.setRetroactivePeriodRecalculationStatus(MaintenanceRetroactivePeriodRecalculationStatus.FAILED);
        view.setRetroactiveProductRecalculationId("product-recalculation-1");
        view.setRetroactiveProductRecalculationVersion("PERIOD_V1");
        view.setRetroactiveProductOriginalCalculationId("calc-original");
        view.setRetroactiveProductOriginalResultHash(hash('o'));
        view.setRetroactiveProductReplacementCalculationId("calc-replacement");
        view.setRetroactiveProductReplacementResultHash(hash('n'));
        view.setRetroactiveProductDirection(MaintenanceBalanceDirection.DEBIT);
        view.setRetroactiveProductAmount(new BigDecimal("20.00"));
        view.setRetroactiveProductCurrency("CNY");
        view.setRetroactiveProductInputHash(hash('i'));
        view.setRetroactiveProductResultHash(hash('r'));
        view.setRetroactiveProductCalculatedAt(PERIOD_START.plusMinutes(1));
        when(caseRepository.findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                "case-1", "tenant-1")).thenReturn(Optional.of(view));
        when(periodRepository
                .findByTenantIdAndMaintenanceIdAndPeriodRecalculationIdOrderByPeriodStartAscPeriodIdAsc(
                        "tenant-1", "case-1", "period-recalculation-1"))
                .thenReturn(List.of(periodView()));

        var result = service().recalculate(input());

        assertEquals(MaintenanceRetroactivePeriodRecalculationStatus.COMPLETED, result.status());
        verify(productPort, never()).recalculate(any());
        verify(billingPort).adjust(any());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(captor.capture());
        assertEquals(StartMaintenanceRetroactivePeriodRecalculationCommand.class,
                captor.getAllValues().getFirst().getClass());
        assertEquals(CompleteMaintenanceRetroactivePeriodRecalculationCommand.class,
                captor.getAllValues().get(1).getClass());
    }

    @Test
    void shouldRejectAmbiguousPremiumCalculationCheckpoint() {
        MaintenanceView view = readyView();
        view.setPremiumCalculationCheckpointConflict(true);
        when(caseRepository.findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                "case-1", "tenant-1")).thenReturn(Optional.of(view));

        assertThrows(MaintenanceValidationException.class, () -> service().recalculate(input()));

        verify(productPort, never()).recalculate(any());
        verify(billingPort, never()).adjust(any());
    }

    private MaintenanceRetroactivePeriodRecalculationApplicationService service() {
        return new MaintenanceRetroactivePeriodRecalculationApplicationService(
                commandGateway, caseRepository, impactRepository, periodRepository, productPort, billingPort);
    }

    private MaintenanceView readyView() {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("case-1");
        view.setPolicyId("policy-1");
        view.setCustomerId("customer-1");
        view.setEffectiveTimeType(EffectiveTimeType.RETROACTIVE);
        view.setOriginalCalculationId("calc-original");
        view.setReplacementCalculationId("calc-replacement");
        view.setRetroactiveImpactAnalysisId("analysis-1");
        view.setRetroactiveImpactAnalysisVersion(1);
        view.setRetroactiveImpactStatus(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED);
        view.setRetroactiveImpactResultHash(hash('a'));
        view.setRetroactiveImpactScopeFrom(LocalDateTime.of(2026, 6, 1, 0, 0));
        view.setRetroactiveImpactScopeTo(LocalDateTime.of(2026, 8, 26, 0, 0));
        return view;
    }

    private MaintenanceRetroactiveImpactItemView impactItem() {
        MaintenanceRetroactiveImpactItemView view = new MaintenanceRetroactiveImpactItemView();
        view.setItemId("BILLING:bill-1");
        view.setReferenceId("bill-1");
        view.setSourceDomain(MaintenanceRetroactiveImpactDomain.BILLING);
        view.setImpactType(MaintenanceRetroactiveImpactType.PREMIUM_BILL);
        view.setOccurredAt(PERIOD_START);
        view.setAmount(new BigDecimal("100.00"));
        view.setCurrency("CNY");
        view.setEvidenceHash(hash('s'));
        return view;
    }

    private RecalculationFact productFact() {
        return new RecalculationFact(
                "tenant-1", "case-1", "policy-1", "analysis-1", 1, hash('a'),
                "product-recalculation-1", "PERIOD_V1", "mpr-request-1", "calc-original", hash('o'),
                "calc-replacement", hash('n'), MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20.00"), "CNY", hash('i'), hash('r'), PERIOD_START.plusMinutes(1),
                List.of(productPeriod()));
    }

    private MaintenanceRetroactiveProductPeriodDifference productPeriod() {
        return new MaintenanceRetroactiveProductPeriodDifference(
                "BILLING:bill-1", "bill-1", PERIOD_START, new BigDecimal("100.00"),
                new BigDecimal("120.00"), MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"),
                "CNY", hash('s'), hash('p'));
    }

    private AdjustmentFact billingFact() {
        return new AdjustmentFact(
                "tenant-1", "case-1", "policy-1", "customer-1", "analysis-1", 1, hash('a'),
                "product-recalculation-1", "PERIOD_V1", hash('i'), hash('r'), "billing-batch-1",
                "POSTED", 1, 0, hash('b'), hash('c'), PERIOD_START.plusMinutes(2),
                List.of(new MaintenanceRetroactiveBillingPeriodAdjustment(
                        "BILLING:bill-1", "bill-1", "2026-07", PERIOD_START,
                        new BigDecimal("100.00"), new BigDecimal("120.00"),
                        MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"), "CNY", "POSTED",
                        hash('s'), hash('p'), hash('z'))));
    }

    private MaintenanceRetroactivePeriodAdjustmentView periodView() {
        MaintenanceRetroactivePeriodAdjustmentView view = new MaintenanceRetroactivePeriodAdjustmentView();
        view.setPeriodId("BILLING:bill-1");
        view.setSourceReferenceId("bill-1");
        view.setPeriodStart(PERIOD_START);
        view.setOriginalAmount(new BigDecimal("100.00"));
        view.setRecalculatedAmount(new BigDecimal("120.00"));
        view.setDirection(MaintenanceBalanceDirection.DEBIT);
        view.setDifferenceAmount(new BigDecimal("20.00"));
        view.setCurrency("CNY");
        view.setSourceEvidenceHash(hash('s'));
        view.setProductResultHash(hash('p'));
        return view;
    }

    private MaintenanceRetroactivePeriodRecalculationInput input() {
        return new MaintenanceRetroactivePeriodRecalculationInput(
                "case-1", "operation-1", "operator-1", "tenant-1");
    }

    private String hash(char value) {
        return String.valueOf((char) ('a' + Math.floorMod(value, 6))).repeat(64);
    }
}
