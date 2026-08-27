package com.titanium.maintenance.application.orchestration.configuration;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.maintenance.exception.MaintenanceConfigurationNotFoundException;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationAuditPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;

@ExtendWith(MockitoExtension.class)
class MaintenanceConfigurationQueryApplicationServiceTest {

    @Mock
    private MaintenanceItemConfigurationRepository repository;

    private MaintenanceConfigurationQueryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceConfigurationQueryApplicationService(repository);
    }

    @Test
    void shouldReturnAuditHistoryForDeletedDraftInsideTenantBoundary() {
        ConfigurationAuditPage page = new ConfigurationAuditPage(List.of(), 1, 0, 20);
        when(repository.findAuditHistory("tenant-1", "deleted-config", 0, 20)).thenReturn(page);

        ConfigurationAuditPage result = service.findAuditHistory(
                "tenant-1", "deleted-config", 0, 20);

        assertSame(page, result);
        verify(repository).findAuditHistory("tenant-1", "deleted-config", 0, 20);
    }

    @Test
    void shouldReportNotFoundWhenNeitherConfigurationNorAuditExists() {
        when(repository.findAuditHistory("tenant-1", "missing-config", 0, 20))
                .thenReturn(new ConfigurationAuditPage(List.of(), 0, 0, 20));

        assertThrows(MaintenanceConfigurationNotFoundException.class,
                () -> service.findAuditHistory("tenant-1", "missing-config", 0, 20));
    }

    @Test
    void shouldResolveEffectiveConfigurationInsideTenantBoundary() {
        LocalDateTime businessTime = LocalDateTime.of(2026, 8, 25, 0, 0);
        StoredConfiguration stored = mock(StoredConfiguration.class);
        when(repository.findEffective("tenant-1", "CONTACT_CHANGE", businessTime))
                .thenReturn(Optional.of(stored));

        StoredConfiguration result = service.resolveEffective(
                "tenant-1", "CONTACT_CHANGE", businessTime);

        assertSame(stored, result);
        verify(repository).findEffective("tenant-1", "CONTACT_CHANGE", businessTime);
    }
}
