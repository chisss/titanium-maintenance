package com.titanium.maintenance.application.orchestration.configuration;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationOperationContext;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationCriteria;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationResult;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceConfigurationAction;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.configuration.control.MaintenanceAccessRule;
import com.titanium.maintenance.configuration.control.MaintenanceChannelCapability;
import com.titanium.maintenance.configuration.control.MaintenanceFeeRule;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.configuration.control.MaintenanceOutputRule;
import com.titanium.maintenance.exception.MaintenanceConfigurationConflictException;
import com.titanium.maintenance.exception.MaintenanceConfigurationFeatureDisabledException;
import com.titanium.maintenance.exception.MaintenanceConfigurationPreconditionFailedException;
import com.titanium.maintenance.port.MaintenanceConfigurationFeaturePort;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;

@ExtendWith(MockitoExtension.class)
class MaintenanceConfigurationManagementApplicationServiceTest {

    private static final LocalDateTime OPERATED_AT = LocalDateTime.of(2026, 8, 24, 12, 0);

    @Mock
    private MaintenanceItemConfigurationRepository repository;
    @Mock
    private MaintenanceConfigurationValidator validator;
    @Mock
    private MaintenanceConfigurationFeaturePort featurePort;

    private MaintenanceConfigurationManagementApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceConfigurationManagementApplicationService(
                repository, validator, featurePort);
    }

    @Test
    void shouldRejectStaleRowVersionBeforeCallingExternalValidator() {
        MaintenanceItemConfiguration configuration = approvedConfiguration();
        when(repository.findById("tenant-1", "config-1"))
                .thenReturn(Optional.of(new StoredConfiguration(configuration, 3L)));

        assertThrows(MaintenanceConfigurationPreconditionFailedException.class,
                () -> service.publish("config-1", 2L, criteria(), context()));

        verify(validator, never()).validateAndRequire(any(), any(), any(), any());
        verify(repository, never()).save(any(), any(Long.class), any());
    }

    @Test
    void shouldRejectPublishedEffectivePeriodOverlap() {
        MaintenanceItemConfiguration configuration = approvedConfiguration();
        when(repository.findById("tenant-1", "config-1"))
                .thenReturn(Optional.of(new StoredConfiguration(configuration, 3L)));
        when(validator.validateAndRequire(eq("tenant-1"), any(), eq(criteria()), eq(OPERATED_AT)))
                .thenReturn(new MaintenanceConfigurationValidationResult(
                        true, List.of(), "catalog-v1", "a".repeat(64), "reference-v1", OPERATED_AT));
        when(featurePort.isWriteEnabled("tenant-1")).thenReturn(true);
        when(repository.existsPublishedOverlap(eq("tenant-1"), eq("CONTACT_CHANGE"), eq("config-1"),
                any(), any())).thenReturn(true);

        assertThrows(MaintenanceConfigurationConflictException.class,
                () -> service.publish("config-1", 3L, criteria(), context()));

        verify(repository, never()).save(any(), any(Long.class), any());
    }

    @Test
    void shouldRejectDraftCreationWhenFeatureIsDisabled() {
        assertThrows(MaintenanceConfigurationFeatureDisabledException.class,
                () -> service.createDraft("config-1", definition(), OPERATED_AT.plusDays(1), null, context()));

        verify(repository, never()).existsByBusinessKey(any(), any(), any());
        verify(repository, never()).save(any(), any(Long.class), any());
    }

    @Test
    void shouldRejectFirstPublishWhenFeatureIsDisabled() {
        MaintenanceItemConfiguration configuration = approvedConfiguration();
        when(repository.findById("tenant-1", "config-1"))
                .thenReturn(Optional.of(new StoredConfiguration(configuration, 3L)));

        assertThrows(MaintenanceConfigurationFeatureDisabledException.class,
                () -> service.publish("config-1", 3L, criteria(), context()));

        verify(validator, never()).validateAndRequire(any(), any(), any(), any());
        verify(repository, never()).save(any(), any(Long.class), any());
    }

    @Test
    void shouldAlwaysLoadConfigurationInsideTenantBoundary() {
        when(repository.findById("tenant-1", "config-1")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.retire("config-1", 1L, context()));

        verify(repository).findById("tenant-1", "config-1");
    }

    @Test
    void shouldTreatRepeatedSubmitAtCurrentVersionAsIdempotent() {
        MaintenanceItemConfiguration configuration = MaintenanceItemConfiguration.createDraft(
                "config-1", "tenant-1", definition(), OPERATED_AT.plusDays(1), null,
                "maker", OPERATED_AT.minusMinutes(2));
        configuration.submitForApproval("maker", OPERATED_AT.minusMinutes(1));
        StoredConfiguration stored = new StoredConfiguration(configuration, 3L);
        when(repository.findById("tenant-1", "config-1")).thenReturn(Optional.of(stored));

        StoredConfiguration result = service.submitForApproval("config-1", 3L, criteria(), context());

        assertSame(stored, result);
        verify(validator, never()).validateAndRequire(any(), any(), any(), any());
        verify(repository, never()).save(any(), any(Long.class), any());
    }

    @Test
    void shouldRecordFinalAuditBeforeDeletingDraft() {
        MaintenanceItemConfiguration configuration = MaintenanceItemConfiguration.createDraft(
                "config-1", "tenant-1", definition(), OPERATED_AT.plusDays(1), null,
                "maker", OPERATED_AT.minusMinutes(1));
        when(repository.findById("tenant-1", "config-1"))
                .thenReturn(Optional.of(new StoredConfiguration(configuration, 1L)));

        service.deleteDraft("config-1", 1L, context());

        assertSame(MaintenanceConfigurationAction.DRAFT_DELETED,
                configuration.getAuditTrail().getLast().action());
        verify(repository).deleteDraft(eq(configuration), eq(1L), any());
    }

    private MaintenanceItemConfiguration approvedConfiguration() {
        MaintenanceItemConfiguration configuration = MaintenanceItemConfiguration.createDraft(
                "config-1", "tenant-1", definition(), OPERATED_AT.plusDays(1), null,
                "maker", OPERATED_AT.minusMinutes(3));
        configuration.submitForApproval("maker", OPERATED_AT.minusMinutes(2));
        configuration.approve("checker", OPERATED_AT.minusMinutes(1));
        return configuration;
    }

    private MaintenanceItemDefinition definition() {
        MaintenanceItemControls controls = new MaintenanceItemControls(
                Set.of(MaintenanceChannelCapability.manualApproval(MaintenanceChannel.MANUAL)),
                List.of(), Set.of(), "APPROVAL_STANDARD", MaintenanceFeeRule.none(),
                new MaintenanceAccessRule(Set.of("maintenance:item:operate"),
                        Set.of("maintenance:item:view")),
                MaintenanceOutputRule.empty());
        return new MaintenanceItemDefinition("CONTACT_CHANGE", "1.0.0", "联系方式变更",
                MaintenanceItemCategory.BASIC_INFORMATION, Set.of(MaintenanceChannel.MANUAL),
                List.of(), List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), true, controls);
    }

    private MaintenanceConfigurationValidationCriteria criteria() {
        return new MaintenanceConfigurationValidationCriteria(
                "LIFE", "INDIVIDUAL", LocalDate.of(2026, 8, 24));
    }

    private MaintenanceConfigurationOperationContext context() {
        return new MaintenanceConfigurationOperationContext(
                "tenant-1", "publisher", "127.0.0.1", "correlation-1", OPERATED_AT);
    }
}
