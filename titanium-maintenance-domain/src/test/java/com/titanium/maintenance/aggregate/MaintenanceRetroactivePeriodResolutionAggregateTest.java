package com.titanium.maintenance.aggregate;

import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.axonframework.test.matchers.Matchers.payloadsMatching;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.command.CompleteMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactivePeriodResolutionCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.event.MaintenanceCaseInitializationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodRecalculationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactivePeriodResolutionStartedEvent;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveBillingAdjustmentEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveBillingPeriodAdjustment;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodRecalculation;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolution;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolutionEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactivePeriodResolutionLine;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductPeriodDifference;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductRecalculationEvidence;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

class MaintenanceRetroactivePeriodResolutionAggregateTest {

    private static final MaintenanceId ID = MaintenanceId.of("case-retroactive-1");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 26, 13, 0);
    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 7, 1, 0, 0);

    private FixtureConfiguration<Maintenance> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Maintenance.class);
    }

    @Test
    void shouldStartAndCompleteClosedPeriodResolution() {
        StartMaintenanceRetroactivePeriodResolutionCommand start = startCommand();
        MaintenanceRetroactivePeriodResolution resolving = MaintenanceRetroactivePeriodResolution.start(
                "resolution-1", "operation-1", hash('q'), "billing-batch-1", hash('b'),
                "2026-08", "结转至当前开放期间", NOW);

        fixture.given(baseEvents())
                .when(start)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceRetroactivePeriodResolutionStartedEvent.class))));

        fixture.given(concat(baseEvents(), new MaintenanceRetroactivePeriodResolutionStartedEvent(
                        ID, resolving, "operator-1", "tenant-1")))
                .when(new CompleteMaintenanceRetroactivePeriodResolutionCommand(
                        ID, "resolution-1", "operation-1", resolutionEvidence(),
                        NOW.plusMinutes(1), "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceRetroactivePeriodResolutionCompletedEvent.class))))
                .expectState(aggregate -> assertEquals(
                        MaintenanceRetroactivePeriodResolutionStatus.COMPLETED,
                        aggregate.getRetroactivePeriodResolution().status()));
    }

    @Test
    void shouldRejectResolutionForDifferentBillingBatch() {
        StartMaintenanceRetroactivePeriodResolutionCommand invalid =
                new StartMaintenanceRetroactivePeriodResolutionCommand(
                        ID, "resolution-1", "operation-1", hash('q'), "billing-batch-other",
                        hash('b'), "2026-08", "结转至当前开放期间", NOW, "operator-1");

        fixture.given(baseEvents())
                .when(invalid)
                .expectExceptionMessage(
                        "命令 StartMaintenanceRetroactivePeriodResolutionCommand 字段 billingBatch 校验失败: "
                                + "关闭期间处理请求与当前Billing批次不一致");
    }

    private Object[] baseEvents() {
        return new Object[] {
                new MaintenanceCreatedEvent(
                        ID, PolicyId.of("policy-1"), CustomerId.of("customer-1"),
                        MaintenanceType.POLICY_INFO_CHANGE, EffectiveTimeType.RETROACTIVE,
                        EFFECTIVE_AT, "追溯案件", NOW.minusMinutes(3), "operator-1", "tenant-1"),
                new MaintenanceCaseInitializationCompletedEvent(
                        ID, List.of("POLICY_INFO_CHANGE"), NOW.minusMinutes(3), "operator-1", "tenant-1"),
                new MaintenanceRetroactiveImpactAnalysisCompletedEvent(
                        ID, impactAnalysis(), "operator-1", "tenant-1"),
                new MaintenanceRetroactivePeriodRecalculationCompletedEvent(
                        ID, periodRecalculation(), "operator-1", "tenant-1")
        };
    }

    private Object[] concat(Object[] events, Object event) {
        Object[] result = java.util.Arrays.copyOf(events, events.length + 1);
        result[events.length] = event;
        return result;
    }

    private StartMaintenanceRetroactivePeriodResolutionCommand startCommand() {
        return new StartMaintenanceRetroactivePeriodResolutionCommand(
                ID, "resolution-1", "operation-1", hash('q'), "billing-batch-1", hash('b'),
                "2026-08", "结转至当前开放期间", NOW, "operator-1");
    }

    private MaintenanceRetroactiveImpactAnalysis impactAnalysis() {
        return MaintenanceRetroactiveImpactAnalysis.start(
                        "analysis-1", 1, "analysis-operation-1", hash('a'),
                        EFFECTIVE_AT, NOW, NOW.minusMinutes(2))
                .complete(MaintenanceRetroactiveImpactAnalysis.requiredDomains(), List.of(),
                        "impact-v1", hash('i'), NOW.minusMinutes(1));
    }

    private MaintenanceRetroactivePeriodRecalculation periodRecalculation() {
        return MaintenanceRetroactivePeriodRecalculation.start(
                        "period-recalculation-1", 1, "recalculation-operation-1", hash('q'),
                        "analysis-1", 1, hash('i'), NOW.minusSeconds(30))
                .recordProduct(productEvidence(), NOW.minusSeconds(20))
                .completeBilling(billingEvidence(), NOW.minusSeconds(10));
    }

    private MaintenanceRetroactiveProductRecalculationEvidence productEvidence() {
        return new MaintenanceRetroactiveProductRecalculationEvidence(
                "product-recalculation-1", "PERIOD_V1", "calc-original", hash('o'),
                "calc-replacement", hash('n'), MaintenanceBalanceDirection.DEBIT,
                new BigDecimal("20.00"), "CNY", hash('p'), hash('r'), NOW.minusSeconds(20),
                List.of(new MaintenanceRetroactiveProductPeriodDifference(
                        "BILLING:bill-1", "bill-1", EFFECTIVE_AT, new BigDecimal("100.00"),
                        new BigDecimal("120.00"), MaintenanceBalanceDirection.DEBIT,
                        new BigDecimal("20.00"), "CNY", hash('s'), hash('p'))));
    }

    private MaintenanceRetroactiveBillingAdjustmentEvidence billingEvidence() {
        return new MaintenanceRetroactiveBillingAdjustmentEvidence(
                "billing-batch-1", "REVIEW_REQUIRED", 0, 1, hash('a'), hash('b'),
                NOW.minusSeconds(10), List.of(new MaintenanceRetroactiveBillingPeriodAdjustment(
                        "BILLING:bill-1", "bill-1", "2026-07", EFFECTIVE_AT,
                        new BigDecimal("100.00"), new BigDecimal("120.00"),
                        MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"), "CNY",
                        "CLOSED_PERIOD_REVIEW", hash('s'), hash('p'), hash('z'))));
    }

    private MaintenanceRetroactivePeriodResolutionEvidence resolutionEvidence() {
        return new MaintenanceRetroactivePeriodResolutionEvidence(
                "billing-resolution-1", "mrr-request-1", "billing-batch-1", hash('b'),
                "2026-08", 1, hash('q'), hash('r'), "结转至当前开放期间", "operator-1",
                NOW.plusMinutes(1), List.of(new MaintenanceRetroactivePeriodResolutionLine(
                        "BILLING:bill-1", "2026-07", "2026-08", MaintenanceBalanceDirection.DEBIT,
                        new BigDecimal("20.00"), "CNY", "posting-1", hash('z'), hash('l'))));
    }

    private String hash(char value) {
        return String.valueOf((char) ('a' + Math.floorMod(value, 6))).repeat(64);
    }
}
