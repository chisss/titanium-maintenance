package com.titanium.maintenance.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.maintenance.application.model.MaintenanceSearchPageResult;
import com.titanium.maintenance.command.CalculateMaintenancePremiumCommand;
import com.titanium.maintenance.command.ExecuteMaintenanceCommand;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.exception.MaintenanceLegacyCreationDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceLegacyExecutionDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceLegacyPremiumCalculationDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.port.CustomerServicePort;
import com.titanium.maintenance.port.MaintenanceLegacyCreationFeaturePort;
import com.titanium.maintenance.port.MaintenanceLegacyExecutionFeaturePort;
import com.titanium.maintenance.port.MaintenanceLegacyPremiumCalculationFeaturePort;
import com.titanium.maintenance.port.PolicyServicePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceExclusionRepository;

@ExtendWith(MockitoExtension.class)
class MaintenanceApplicationServiceSearchTest {

    @Mock private CommandGateway commandGateway;
    @Mock private MaintenanceViewRepository repository;
    @Mock private MaintenanceExclusionRepository exclusionRepository;
    @Mock private PolicyServicePort policyServicePort;
    @Mock private CustomerServicePort customerServicePort;
    @Mock private MaintenanceLegacyCreationFeaturePort legacyCreationFeaturePort;
    @Mock private MaintenanceLegacyPremiumCalculationFeaturePort legacyPremiumCalculationFeaturePort;
    @Mock private MaintenanceLegacyExecutionFeaturePort legacyExecutionFeaturePort;

    private MaintenanceApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceApplicationService(
                commandGateway, repository, exclusionRepository, policyServicePort, customerServicePort,
                legacyCreationFeaturePort, legacyPremiumCalculationFeaturePort, legacyExecutionFeaturePort);
    }

    @Test
    void shouldRejectLegacyCreationBeforeCallingDependenciesWhenFeatureIsDisabled() {
        when(legacyCreationFeaturePort.isEnabled("tenant-1")).thenReturn(false);

        assertThrows(MaintenanceLegacyCreationDisabledException.class,
                () -> service.createMaintenanceCase(
                        "policy-1", "customer-1", null, null, null,
                        "兼容建案", "operator-1", "tenant-1"));

        verifyNoInteractions(policyServicePort, customerServicePort, commandGateway, repository, exclusionRepository);
    }

    @Test
    void shouldRejectLegacyPremiumCalculationBeforeReadingCaseWhenFeatureIsDisabled() {
        when(legacyPremiumCalculationFeaturePort.isEnabled("tenant-1")).thenReturn(false);

        assertThrows(MaintenanceLegacyPremiumCalculationDisabledException.class,
                () -> service.calculateMaintenancePremium(
                        "maintenance-1", new BigDecimal("100.00"), BigDecimal.ZERO,
                        "旧版人工金额", "operator-1", "tenant-1"));

        verifyNoInteractions(repository, commandGateway);
    }

    @Test
    void shouldPreserveLegacyPremiumCalculationWhenFeatureIsEnabled() {
        when(legacyPremiumCalculationFeaturePort.isEnabled("tenant-1")).thenReturn(true);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(view("maintenance-1")));
        when(commandGateway.send(any(CalculateMaintenancePremiumCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        String maintenanceId = service.calculateMaintenancePremium(
                        "maintenance-1", new BigDecimal("100.00"), BigDecimal.ZERO,
                        "旧版人工金额", "operator-1", "tenant-1")
                .join();

        assertEquals("maintenance-1", maintenanceId);
        verify(commandGateway).send(any(CalculateMaintenancePremiumCommand.class));
    }

    @Test
    void shouldRejectLegacyExecutionBeforeReadingCaseWhenFeatureIsDisabled() {
        when(legacyExecutionFeaturePort.isEnabled("tenant-1")).thenReturn(false);

        assertThrows(MaintenanceLegacyExecutionDisabledException.class,
                () -> service.executeMaintenance(
                        "maintenance-1", LocalDateTime.parse("2026-08-26T15:30:00"),
                        "旧版整案执行", "operator-1", "tenant-1"));

        verifyNoInteractions(repository, commandGateway);
    }

    @Test
    void shouldPreserveLegacyExecutionWhenFeatureIsEnabled() {
        when(legacyExecutionFeaturePort.isEnabled("tenant-1")).thenReturn(true);
        MaintenanceView approved = view("maintenance-1");
        approved.setStatus(MaintenanceStatus.APPROVED);
        when(repository.findByMaintenanceIdAndTenantId("maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(approved));
        when(commandGateway.send(any(ExecuteMaintenanceCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        String maintenanceId = service.executeMaintenance(
                        "maintenance-1", LocalDateTime.parse("2026-08-26T15:30:00"),
                        "旧版整案执行", "operator-1", "tenant-1")
                .join();

        assertEquals("maintenance-1", maintenanceId);
        verify(commandGateway).send(any(ExecuteMaintenanceCommand.class));
    }

    @Test
    void shouldCountFilteredRowsBeforePaging() {
        when(repository.findByTenantIdAndStatus("tenant-1", MaintenanceStatus.PENDING))
                .thenReturn(List.of(view("m-1"), view("m-2"), view("m-3")));

        MaintenanceSearchPageResult result = service.searchMaintenancePage(
                null, null, null, "PENDING", 1, 2, "tenant-1");

        assertEquals(3, result.total());
        assertEquals(1, result.list().size());
        assertEquals("m-3", result.list().getFirst().getId());
        assertEquals(2, result.pageNum());
        assertEquals(2, result.totalPages());
    }

    @Test
    void shouldListTenantMaintenancesWhenNoFilterProvided() {
        when(repository.findByTenantIdOrderByCreateTimeDesc("tenant-1"))
                .thenReturn(List.of(view("m-3"), view("m-2"), view("m-1")));

        MaintenanceSearchPageResult result = service.searchMaintenancePage(
                null, null, null, null, 0, 2, "tenant-1");

        assertEquals(3, result.total());
        assertEquals(List.of("m-3", "m-2"), result.list().stream()
                .map(item -> item.getId())
                .toList());
    }

    @Test
    void shouldExcludeUninitializedIndependentCasesFromSearch() {
        when(repository.findByTenantIdOrderByCreateTimeDesc("tenant-1"))
                .thenReturn(List.of(view("legacy"), independentView("ready", true), independentView("opening", false)));

        MaintenanceSearchPageResult result = service.searchMaintenancePage(
                null, null, null, null, 0, 10, "tenant-1");

        assertEquals(2, result.total());
        assertEquals(List.of("legacy", "ready"), result.list().stream()
                .map(item -> item.getId())
                .toList());
    }

    @Test
    void shouldHideUninitializedIndependentCaseFromIdLookup() {
        when(repository.findByMaintenanceIdAndTenantId("opening", "tenant-1"))
                .thenReturn(Optional.of(independentView("opening", false)));

        assertThrows(MaintenanceNotFoundException.class,
                () -> service.findMaintenanceById("opening", "tenant-1"));
    }

    private MaintenanceView view(String id) {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId(id);
        view.setTenantId("tenant-1");
        view.setStatus(MaintenanceStatus.PENDING);
        return view;
    }

    private MaintenanceView independentView(String id, boolean initializationCompleted) {
        MaintenanceView view = view(id);
        view.setIndependentCase(true);
        view.setInitializationCompleted(initializationCompleted);
        return view;
    }
}
