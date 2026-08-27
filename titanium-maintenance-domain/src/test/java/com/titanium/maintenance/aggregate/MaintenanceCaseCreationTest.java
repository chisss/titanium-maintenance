package com.titanium.maintenance.aggregate;

import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.axonframework.test.matchers.Matchers.payloadsMatching;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.command.CreateMaintenanceCaseCommand;
import com.titanium.maintenance.command.CreateMaintenanceCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.event.MaintenanceCaseItemsPlannedEvent;
import com.titanium.maintenance.event.MaintenanceCaseOpenedEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenancePolicySnapshotCapturedEvent;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

class MaintenanceCaseCreationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 12, 0);

    private FixtureConfiguration<Maintenance> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Maintenance.class);
    }

    @Test
    void shouldCreateCaseWithCompatibleAndIdempotencyEvents() {
        CreateMaintenanceCaseCommand command = command("request-1", "联系方式变更");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceCreatedEvent.class),
                        instanceOf(MaintenanceCaseOpenedEvent.class),
                        instanceOf(MaintenancePolicySnapshotCapturedEvent.class),
                        instanceOf(MaintenanceCaseItemsPlannedEvent.class))))
                .expectState(aggregate -> {
                    assertEquals(command.id(), aggregate.getId());
                    assertEquals(MaintenanceChannel.MANUAL, aggregate.getSource());
                    assertEquals("request-1", aggregate.getClientRequestKey());
                    assertEquals(command.requestFingerprint(), aggregate.getCreationRequestFingerprint());
                    assertEquals(command.policySnapshot(), aggregate.getPolicySnapshot());
                    assertEquals(List.of("POLICY_INFO_CHANGE"), aggregate.getPlannedItemCodes());
                });
    }

    @Test
    void shouldTreatSameKeyAndPayloadAsIdempotent() {
        CreateMaintenanceCaseCommand command = command("request-1", "联系方式变更");

        fixture.given(createdEvent(command), openedEvent(command), snapshotCapturedEvent(command),
                        plannedEvent(command))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void shouldBackfillSnapshotForCaseCreatedBeforeSnapshotEventWasIntroduced() {
        CreateMaintenanceCaseCommand command = command("request-1", "联系方式变更");

        fixture.given(createdEvent(command), openedEvent(command))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenancePolicySnapshotCapturedEvent.class),
                        instanceOf(MaintenanceCaseItemsPlannedEvent.class))))
                .expectState(aggregate -> assertEquals(command.policySnapshot(), aggregate.getPolicySnapshot()));
    }

    @Test
    void shouldRejectSameKeyWithDifferentPayload() {
        CreateMaintenanceCaseCommand original = command("request-1", "联系方式变更");
        CreateMaintenanceCaseCommand conflicting = command("request-1", "变更后的描述");

        fixture.given(createdEvent(original), openedEvent(original), snapshotCapturedEvent(original))
                .when(conflicting)
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldRejectSameRequestWhenPolicySnapshotConflicts() {
        CreateMaintenanceCaseCommand original = command("request-1", "联系方式变更");
        PolicyMaintenanceSnapshot changedSnapshot = snapshot(
                "policy-1", "customer-1", "tenant-1", 8L, PolicyStatus.EFFECTIVE);
        CreateMaintenanceCaseCommand conflicting = CreateMaintenanceCaseCommand.of(
                "policy-1", MaintenanceType.POLICY_INFO_CHANGE,
                EffectiveTimeType.IMMEDIATE, null, "联系方式变更", changedSnapshot,
                "request-1", MaintenanceChannel.MANUAL, "operator-1", "tenant-1");

        fixture.given(createdEvent(original), openedEvent(original), snapshotCapturedEvent(original))
                .when(conflicting)
                .expectException(MaintenanceValidationException.class);
    }

    @Test
    void shouldKeepLegacyCreationEventContract() {
        CreateMaintenanceCommand command = new CreateMaintenanceCommand(
                MaintenanceId.of("legacy-1"), PolicyId.of("policy-1"), CustomerId.of("customer-1"),
                MaintenanceType.POLICY_INFO_CHANGE, EffectiveTimeType.IMMEDIATE, null,
                "历史入口", "operator-1", "tenant-1");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(MaintenanceCreatedEvent.class))));
    }

    @Test
    void shouldRejectBlankPolicyIdAtCommandBoundary() {
        assertThrows(MaintenanceValidationException.class, () -> CreateMaintenanceCaseCommand.of(
                " ", MaintenanceType.POLICY_INFO_CHANGE,
                EffectiveTimeType.IMMEDIATE, null, "联系方式变更", snapshot(), "request-1",
                MaintenanceChannel.MANUAL, "operator-1", "tenant-1"));
    }

    @Test
    void shouldRejectMissingPolicySnapshotAtCommandBoundary() {
        assertThrows(MaintenanceValidationException.class, () -> CreateMaintenanceCaseCommand.of(
                "policy-1", MaintenanceType.POLICY_INFO_CHANGE,
                EffectiveTimeType.IMMEDIATE, null, "联系方式变更", null, "request-1",
                MaintenanceChannel.MANUAL, "operator-1", "tenant-1"));
    }

    @Test
    void shouldRequireSpecificTimeForFutureEffectivity() {
        assertThrows(MaintenanceValidationException.class, () -> CreateMaintenanceCaseCommand.of(
                "policy-1", MaintenanceType.POLICY_INFO_CHANGE,
                EffectiveTimeType.FUTURE, null, "联系方式变更", snapshot(), "request-1",
                MaintenanceChannel.MANUAL, "operator-1", "tenant-1"));
    }

    private CreateMaintenanceCaseCommand command(String requestKey, String description) {
        return CreateMaintenanceCaseCommand.of(
                "policy-1", MaintenanceType.POLICY_INFO_CHANGE,
                EffectiveTimeType.IMMEDIATE, null, description, snapshot(), requestKey,
                MaintenanceChannel.MANUAL, "operator-1", "tenant-1");
    }

    private PolicyMaintenanceSnapshot snapshot() {
        return snapshot("policy-1", "customer-1", "tenant-1", 7L, PolicyStatus.EFFECTIVE);
    }

    private PolicyMaintenanceSnapshot snapshot(
            String policyId,
            String customerId,
            String tenantId,
            long policyVersion,
            PolicyStatus status) {
        OffsetDateTime capturedAt = OffsetDateTime.of(2026, 8, 24, 11, 30, 0, 0, ZoneOffset.ofHours(8));
        return new PolicyMaintenanceSnapshot(
                tenantId, PolicyId.of(policyId), "P202608240001", CustomerId.of(customerId),
                "product-1", "product-v3", "plan-v2", status, policyVersion, capturedAt,
                new MaintenanceSnapshotReference(
                        "policy/" + policyId + "/versions/" + policyVersion,
                        "a".repeat(64), policyVersion, capturedAt),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13800000000")));
    }

    private MaintenanceCreatedEvent createdEvent(CreateMaintenanceCaseCommand command) {
        return new MaintenanceCreatedEvent(
                command.id(), command.policyId(), command.customerId(), command.primaryMaintenanceType(),
                command.effectiveTimeType(), command.specificEffectiveDate(), command.description(), NOW,
                command.createdBy(), command.tenantId());
    }

    private MaintenanceCaseOpenedEvent openedEvent(CreateMaintenanceCaseCommand command) {
        return new MaintenanceCaseOpenedEvent(
                command.id(), command.idempotencyKey().source(), command.idempotencyKey().clientRequestKey(),
                command.requestFingerprint(), NOW, command.createdBy(), command.tenantId());
    }

    private MaintenanceCaseItemsPlannedEvent plannedEvent(CreateMaintenanceCaseCommand command) {
        return new MaintenanceCaseItemsPlannedEvent(
                command.id(), command.selectedItemCodes(), NOW, command.createdBy(), command.tenantId());
    }

    private MaintenancePolicySnapshotCapturedEvent snapshotCapturedEvent(
            CreateMaintenanceCaseCommand command) {
        return new MaintenancePolicySnapshotCapturedEvent(
                command.id(), command.policySnapshot(), NOW, command.createdBy(), command.tenantId());
    }
}
