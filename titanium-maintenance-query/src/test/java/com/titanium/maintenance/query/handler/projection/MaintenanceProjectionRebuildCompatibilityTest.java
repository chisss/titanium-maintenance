package com.titanium.maintenance.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.event.MaintenanceCaseInitializationCompletedEvent;
import com.titanium.maintenance.event.MaintenanceCaseOpenedEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.query.mapper.MaintenanceViewMapper;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

class MaintenanceProjectionRebuildCompatibilityTest {

    private static final MaintenanceId ID = MaintenanceId.of("maintenance-1");
    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-08-25T09:00:00");

    private AtomicReference<MaintenanceView> state;
    private MaintenanceProjectionEventHandler legacyHandler;
    private MaintenanceCaseProjectionEventHandler caseHandler;

    @BeforeEach
    void setUp() {
        state = new AtomicReference<>();
        MaintenanceViewRepository repository = mock(MaintenanceViewRepository.class);
        when(repository.findByMaintenanceIdAndTenantId(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(state.get()));
        when(repository.save(any(MaintenanceView.class))).thenAnswer(invocation -> {
            MaintenanceView saved = invocation.getArgument(0);
            state.set(saved);
            return saved;
        });
        legacyHandler = new MaintenanceProjectionEventHandler(
                repository, Mappers.getMapper(MaintenanceViewMapper.class));
        caseHandler = new MaintenanceCaseProjectionEventHandler(
                repository,
                mock(MaintenanceCaseItemViewRepository.class),
                mock(MaintenanceFieldChangeViewRepository.class),
                mock(MaintenanceSnapshotViewRepository.class));
    }

    @Test
    void shouldKeepLegacyCaseVisibleWhenReplayingOnlyHistoricalCreationEvent() {
        legacyHandler.on(createdEvent());

        assertFalse(state.get().isIndependentCase());
        assertTrue(state.get().isOperatorVisible());
    }

    @Test
    void shouldHideNewCaseUntilCompatibleInitializationSequenceCompletes() {
        legacyHandler.on(createdEvent());
        caseHandler.on(new MaintenanceCaseOpenedEvent(
                ID, MaintenanceChannel.API, "request-1", "a".repeat(64),
                CREATED_AT.plusMinutes(1), "api-client-1", "tenant-1"));

        assertFalse(state.get().isOperatorVisible());

        caseHandler.on(new MaintenanceCaseInitializationCompletedEvent(
                ID, List.of("POLICY_INFO_CHANGE"), CREATED_AT.plusMinutes(2), "api-client-1", "tenant-1"));

        assertTrue(state.get().isIndependentCase());
        assertTrue(state.get().isInitializationCompleted());
        assertTrue(state.get().isOperatorVisible());
    }

    private MaintenanceCreatedEvent createdEvent() {
        return new MaintenanceCreatedEvent(
                ID, PolicyId.of("policy-1"), CustomerId.of("customer-1"),
                MaintenanceType.POLICY_INFO_CHANGE, EffectiveTimeType.IMMEDIATE,
                null, "投影重建兼容验收", CREATED_AT, "operator-1", "tenant-1");
    }
}
