package com.titanium.maintenance.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;

@ExtendWith(MockitoExtension.class)
class MaintenanceQueryServiceImplTest {

    @Mock private MaintenanceViewRepository repository;

    private MaintenanceQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceQueryServiceImpl(repository);
    }

    @Test
    void shouldHideUninitializedIndependentCaseFromSummary() {
        when(repository.findByMaintenanceIdAndTenantId("opening", "tenant-1"))
                .thenReturn(Optional.of(independentView("opening", false)));

        assertTrue(service.getMaintenanceSummary("opening", "tenant-1").isEmpty());
    }

    @Test
    void shouldReturnOnlyOperatorVisiblePolicyCases() {
        when(repository.findByPolicyIdAndTenantId("policy-1", "tenant-1"))
                .thenReturn(List.of(legacyView("legacy"), independentView("ready", true),
                        independentView("opening", false)));

        assertEquals(List.of("legacy", "ready"), service
                .getMaintenanceSummariesByPolicyId("policy-1", "tenant-1").stream()
                .map(result -> result.getMaintenanceId())
                .toList());
    }

    @Test
    void shouldReturnOnlyOperatorVisibleCustomerCases() {
        when(repository.findByCustomerIdAndTenantId("customer-1", "tenant-1"))
                .thenReturn(List.of(independentView("opening", false), independentView("ready", true)));

        assertEquals(List.of("ready"), service
                .getMaintenanceSummariesByCustomerId("customer-1", "tenant-1").stream()
                .map(result -> result.getMaintenanceId())
                .toList());
    }

    private MaintenanceView legacyView(String id) {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId(id);
        view.setTenantId("tenant-1");
        return view;
    }

    private MaintenanceView independentView(String id, boolean initializationCompleted) {
        MaintenanceView view = legacyView(id);
        view.setIndependentCase(true);
        view.setInitializationCompleted(initializationCompleted);
        return view;
    }
}
