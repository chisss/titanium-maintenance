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

import com.titanium.maintenance.command.CompleteMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.event.MaintenanceCaseInitializationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisCompletedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisFailedEvent;
import com.titanium.maintenance.event.MaintenanceRetroactiveImpactAnalysisStartedEvent;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactAnalysis;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

class MaintenanceRetroactiveImpactAnalysisAggregateTest {

    private static final MaintenanceId ID = MaintenanceId.of("case-retroactive-1");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 25, 12, 0);
    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 8, 1, 0, 0);

    private FixtureConfiguration<Maintenance> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Maintenance.class);
    }

    @Test
    void shouldStartAndCompleteStructuredImpactAnalysis() {
        StartMaintenanceRetroactiveImpactAnalysisCommand start = startCommand("operation-1", "analysis-1");
        MaintenanceRetroactiveImpactAnalysis analyzing = analysis("operation-1", "analysis-1", 1);

        fixture.given(createdEvent(), initializedEvent(),
                        new MaintenanceRetroactiveImpactAnalysisStartedEvent(
                                ID, analyzing, "operator-1", "tenant-1"))
                .when(new CompleteMaintenanceRetroactiveImpactAnalysisCommand(
                        ID, "analysis-1", "operation-1",
                        MaintenanceRetroactiveImpactAnalysis.requiredDomains(), List.of(claimItem()),
                        "impact-evidence-v1", "b".repeat(64), NOW.plusMinutes(1), "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceRetroactiveImpactAnalysisCompletedEvent.class))))
                .expectState(aggregate -> {
                    assertEquals(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED,
                            aggregate.getRetroactiveImpactAnalysis().status());
                    assertEquals(1, aggregate.getRetroactiveImpactAnalysis().blockingItemCount());
                });

        fixture.given(createdEvent(), initializedEvent())
                .when(start)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceRetroactiveImpactAnalysisStartedEvent.class))));
    }

    @Test
    void shouldIncrementVersionAfterFailedAnalysis() {
        MaintenanceRetroactiveImpactAnalysis first = analysis("operation-1", "analysis-1", 1);
        MaintenanceRetroactiveImpactAnalysis failed = first.fail(
                "CLAIM_UNAVAILABLE", "理赔取证失败", NOW.plusMinutes(1));

        fixture.given(createdEvent(), initializedEvent(),
                        new MaintenanceRetroactiveImpactAnalysisStartedEvent(
                                ID, first, "operator-1", "tenant-1"),
                        new MaintenanceRetroactiveImpactAnalysisFailedEvent(
                                ID, failed, "operator-1", "tenant-1"))
                .when(startCommand("operation-2", "analysis-2"))
                .expectSuccessfulHandlerExecution()
                .expectState(aggregate -> assertEquals(
                        2, aggregate.getRetroactiveImpactAnalysis().analysisVersion()));
    }

    @Test
    void shouldRecordFailureAndKeepEffectSeparate() {
        MaintenanceRetroactiveImpactAnalysis analyzing = analysis("operation-1", "analysis-1", 1);

        fixture.given(createdEvent(), initializedEvent(),
                        new MaintenanceRetroactiveImpactAnalysisStartedEvent(
                                ID, analyzing, "operator-1", "tenant-1"))
                .when(new FailMaintenanceRetroactiveImpactAnalysisCommand(
                        ID, "analysis-1", "operation-1", "BILLING_UNAVAILABLE",
                        "账务取证失败", NOW.plusMinutes(1), "operator-1"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceRetroactiveImpactAnalysisFailedEvent.class))))
                .expectState(aggregate -> assertEquals(
                        MaintenanceRetroactiveImpactAnalysisStatus.FAILED,
                        aggregate.getRetroactiveImpactAnalysis().status()));
    }

    private StartMaintenanceRetroactiveImpactAnalysisCommand startCommand(
            String operationId,
            String analysisId) {
        return new StartMaintenanceRetroactiveImpactAnalysisCommand(
                ID, analysisId, operationId, "a".repeat(64), EFFECTIVE_AT, NOW, NOW, "operator-1");
    }

    private MaintenanceRetroactiveImpactAnalysis analysis(String operationId, String analysisId, int version) {
        return MaintenanceRetroactiveImpactAnalysis.start(
                analysisId, version, operationId, "a".repeat(64), EFFECTIVE_AT, NOW, NOW);
    }

    private MaintenanceRetroactiveImpactItem claimItem() {
        return new MaintenanceRetroactiveImpactItem(
                "CLAIM:claim-1", MaintenanceRetroactiveImpactDomain.CLAIM,
                MaintenanceRetroactiveImpactType.CLAIM, "claim-1", "CL-1", EFFECTIVE_AT.plusDays(1),
                "PAID", new BigDecimal("1000.00"), "CNY",
                MaintenanceRetroactiveImpactSeverity.BLOCKING,
                MaintenanceRetroactiveImpactItemStatus.PENDING,
                "追溯期间存在已赔付理赔", "claim-view-v1", "c".repeat(64));
    }

    private MaintenanceCreatedEvent createdEvent() {
        return new MaintenanceCreatedEvent(
                ID, PolicyId.of("policy-1"), CustomerId.of("customer-1"),
                MaintenanceType.POLICY_INFO_CHANGE, EffectiveTimeType.RETROACTIVE,
                EFFECTIVE_AT, "追溯案件", NOW, "operator-1", "tenant-1");
    }

    private MaintenanceCaseInitializationCompletedEvent initializedEvent() {
        return new MaintenanceCaseInitializationCompletedEvent(
                ID, List.of("POLICY_INFO_CHANGE"), NOW, "operator-1", "tenant-1");
    }
}
