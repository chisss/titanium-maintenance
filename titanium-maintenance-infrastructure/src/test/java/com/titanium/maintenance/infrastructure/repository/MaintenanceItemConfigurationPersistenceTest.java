package com.titanium.maintenance.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceConfigurationAction;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.exception.MaintenanceConfigurationPreconditionFailedException;
import com.titanium.maintenance.infrastructure.entity.MaintenanceConfigurationAuditDO;
import com.titanium.maintenance.infrastructure.entity.MaintenanceItemConfigurationDO;
import com.titanium.maintenance.infrastructure.repository.jpa.MaintenanceConfigurationAuditJpaRepository;
import com.titanium.maintenance.infrastructure.repository.jpa.MaintenanceItemConfigurationJpaRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationSearchCriteria;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.SaveContext;

@ExtendWith(MockitoExtension.class)
class MaintenanceItemConfigurationPersistenceTest {

    private static final LocalDateTime OPERATED_AT = LocalDateTime.of(2026, 8, 24, 12, 0);

    @Mock
    private MaintenanceItemConfigurationJpaRepository configurationJpaRepository;
    @Mock
    private MaintenanceConfigurationAuditJpaRepository auditJpaRepository;

    private MaintenanceItemConfigurationJsonMapper jsonMapper;
    private JpaMaintenanceItemConfigurationRepository repository;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        jsonMapper = new MaintenanceItemConfigurationJsonMapper(objectMapper);
        repository = new JpaMaintenanceItemConfigurationRepository(
                configurationJpaRepository, auditJpaRepository, jsonMapper);
    }

    @Test
    void shouldRoundTripCompleteConfigurationJson() {
        MaintenanceItemConfiguration source = draft();

        MaintenanceItemConfiguration restored = jsonMapper.fromJson(jsonMapper.toJson(source));

        assertEquals(source.getConfigurationId(), restored.getConfigurationId());
        assertEquals(source.getDefinition(), restored.getDefinition());
        assertEquals(source.getAuditTrail(), restored.getAuditTrail());
    }

    @Test
    void shouldInsertSnapshotAndAppendAuditWithOperationContext() {
        MaintenanceItemConfiguration configuration = draft();
        when(configurationJpaRepository.existsById("config-1")).thenReturn(false);
        when(configurationJpaRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            MaintenanceItemConfigurationDO entity = invocation.getArgument(0);
            entity.setRowVersion(0L);
            return entity;
        });

        repository.save(configuration, MaintenanceItemConfigurationRepository.NEW_CONFIGURATION_VERSION,
                new SaveContext("127.0.0.1", "correlation-1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaintenanceConfigurationAuditDO>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(auditJpaRepository).saveAll(captor.capture());
        MaintenanceConfigurationAuditDO audit = captor.getValue().getFirst();
        assertNull(audit.getBeforeJson());
        assertNotNull(audit.getAfterJson());
        assertEquals("127.0.0.1", audit.getSourceIp());
        assertEquals("correlation-1", audit.getCorrelationId());
        assertEquals("SUCCESS", audit.getOperationResult());
    }

    @Test
    void shouldAppendOnlyNewAuditEntryOnUpdate() {
        MaintenanceItemConfiguration configuration = draft();
        String beforeJson = jsonMapper.toJson(configuration);
        MaintenanceItemConfigurationDO entity = entity(beforeJson, 0L, 1);
        configuration.replaceDraftContent(definition(), OPERATED_AT.plusDays(2), null,
                "editor", OPERATED_AT.plusMinutes(1));
        when(configurationJpaRepository.findByTenantIdAndConfigurationId("tenant-1", "config-1"))
                .thenReturn(Optional.of(entity));
        when(configurationJpaRepository.saveAndFlush(entity)).thenReturn(entity);

        repository.save(configuration, 0L, new SaveContext("127.0.0.1", "correlation-2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaintenanceConfigurationAuditDO>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(auditJpaRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertNotNull(captor.getValue().getFirst().getBeforeJson());
        assertEquals(2, captor.getValue().getFirst().getAuditSequence());
    }

    @Test
    void shouldRejectStaleJpaRowVersion() {
        MaintenanceItemConfiguration configuration = draft();
        MaintenanceItemConfigurationDO entity = entity(jsonMapper.toJson(configuration), 2L, 1);
        when(configurationJpaRepository.findByTenantIdAndConfigurationId("tenant-1", "config-1"))
                .thenReturn(Optional.of(entity));

        assertThrows(MaintenanceConfigurationPreconditionFailedException.class,
                () -> repository.save(configuration, 1L,
                        new SaveContext("127.0.0.1", "correlation-3")));
    }

    @Test
    void shouldSearchConfigurationsInsideTenantBoundary() {
        MaintenanceItemConfiguration configuration = draft();
        MaintenanceItemConfigurationDO entity = entity(jsonMapper.toJson(configuration), 0L, 1);
        LocalDateTime effectiveAt = OPERATED_AT.plusDays(2);
        when(configurationJpaRepository.search(
                eq("tenant-1"), eq("CONTACT_CHANGE"), eq(MaintenanceItemConfigurationStatus.DRAFT),
                eq(effectiveAt), eq(PageRequest.of(1, 20))))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(1, 20), 21));

        var result = repository.search("tenant-1", new ConfigurationSearchCriteria(
                "CONTACT_CHANGE", MaintenanceItemConfigurationStatus.DRAFT, effectiveAt, 1, 20));

        assertEquals(21, result.total());
        assertEquals("tenant-1", result.items().getFirst().configuration().getTenantId());
        verify(configurationJpaRepository).search(
                "tenant-1", "CONTACT_CHANGE", MaintenanceItemConfigurationStatus.DRAFT,
                effectiveAt, PageRequest.of(1, 20));
    }

    @Test
    void shouldDeleteDraftAfterAppendingFinalAudit() {
        MaintenanceItemConfiguration configuration = draft();
        String beforeJson = jsonMapper.toJson(configuration);
        MaintenanceItemConfigurationDO entity = entity(beforeJson, 0L, 1);
        configuration.recordDraftDeletion("editor", OPERATED_AT.plusMinutes(1));
        when(configurationJpaRepository.findByTenantIdAndConfigurationId("tenant-1", "config-1"))
                .thenReturn(Optional.of(entity));

        repository.deleteDraft(configuration, 0L,
                new SaveContext("127.0.0.1", "correlation-delete"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaintenanceConfigurationAuditDO>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(auditJpaRepository).saveAll(captor.capture());
        assertEquals(MaintenanceConfigurationAction.DRAFT_DELETED,
                captor.getValue().getFirst().getAction());
        verify(configurationJpaRepository).delete(entity);
        verify(configurationJpaRepository).flush();
    }

    private MaintenanceItemConfigurationDO entity(String json, long rowVersion, int auditCount) {
        MaintenanceItemConfigurationDO entity = new MaintenanceItemConfigurationDO();
        entity.setConfigurationId("config-1");
        entity.setTenantId("tenant-1");
        entity.setConfigurationJson(json);
        entity.setContentHash(null);
        entity.setAuditEntryCount(auditCount);
        entity.setRowVersion(rowVersion);
        return entity;
    }

    private MaintenanceItemConfiguration draft() {
        return MaintenanceItemConfiguration.createDraft(
                "config-1", "tenant-1", definition(), OPERATED_AT.plusDays(1), null,
                "maker", OPERATED_AT);
    }

    private MaintenanceItemDefinition definition() {
        return new MaintenanceItemDefinition("CONTACT_CHANGE", "1.0.0", "联系方式变更",
                MaintenanceItemCategory.BASIC_INFORMATION, Set.of(MaintenanceChannel.MANUAL),
                List.of(), List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), true);
    }
}
