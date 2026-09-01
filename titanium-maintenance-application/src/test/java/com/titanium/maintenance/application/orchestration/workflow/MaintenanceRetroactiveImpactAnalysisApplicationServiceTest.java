package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.retroactive.MaintenanceRetroactiveImpactAnalysisInput;
import com.titanium.maintenance.command.CompleteMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.FailMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.command.StartMaintenanceRetroactiveImpactAnalysisCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.port.maintenance.MaintenanceRetroactiveImpactSourcePort;
import com.titanium.maintenance.port.maintenance.MaintenanceRetroactiveImpactSourcePort.SourceEvidence;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;

class MaintenanceRetroactiveImpactAnalysisApplicationServiceTest {

    private CommandGateway commandGateway;
    private MaintenanceViewRepository maintenanceViewRepository;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        maintenanceViewRepository = mock(MaintenanceViewRepository.class);
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "case-1", "tenant-1"))
                .thenReturn(Optional.of(retroactiveCase()));
    }

    @Test
    void shouldCompleteOnlyAfterAllRequiredDomainsReturnEvidence() {
        List<MaintenanceRetroactiveImpactSourcePort> ports = requiredPorts();
        MaintenanceRetroactiveImpactAnalysisApplicationService service = service(ports);

        var result = service.analyze(input());

        assertEquals(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED, result.status());
        assertEquals(4, result.itemCount());
        assertEquals(4, result.blockingItemCount());
        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(commands.capture());
        assertEquals(StartMaintenanceRetroactiveImpactAnalysisCommand.class,
                commands.getAllValues().getFirst().getClass());
        CompleteMaintenanceRetroactiveImpactAnalysisCommand completed =
                (CompleteMaintenanceRetroactiveImpactAnalysisCommand) commands.getAllValues().get(1);
        assertEquals(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED, result.status());
        assertEquals(MaintenanceRetroactiveImpactDomain.BILLING, completed.coveredDomains().getFirst());
        assertEquals(MaintenanceRetroactiveImpactDomain.BILLING,
                completed.items().getFirst().sourceDomain());
        ports.forEach(port -> verify(port).collect(any()));
    }

    @Test
    void shouldFailClosedAndRecordFailureWhenRequiredSourceIsMissing() {
        List<MaintenanceRetroactiveImpactSourcePort> ports = requiredPorts();
        MaintenanceRetroactiveImpactAnalysisApplicationService service =
                service(ports.subList(0, ports.size() - 1));

        var result = service.analyze(input());

        assertEquals(MaintenanceRetroactiveImpactAnalysisStatus.FAILED, result.status());
        assertEquals("RETROACTIVE_IMPACT_SOURCE_FAILED", result.failureCode());
        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(commands.capture());
        assertEquals(StartMaintenanceRetroactiveImpactAnalysisCommand.class,
                commands.getAllValues().getFirst().getClass());
        assertEquals(FailMaintenanceRetroactiveImpactAnalysisCommand.class,
                commands.getAllValues().get(1).getClass());
        ports.forEach(port -> verify(port, never()).collect(any()));
    }

    @Test
    void shouldReplayTerminalResultWithoutCallingAuthoritiesAgain() {
        MaintenanceView view = retroactiveCase();
        view.setRetroactiveImpactAnalysisId("ria-existing");
        view.setRetroactiveImpactAnalysisVersion(2);
        view.setRetroactiveImpactOperationId("operation-1");
        view.setRetroactiveImpactStatus(MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED);
        view.setRetroactiveImpactScopeFrom(LocalDateTime.parse("2026-08-01T00:00:00"));
        view.setRetroactiveImpactScopeTo(LocalDateTime.parse("2026-08-25T18:00:00"));
        view.setRetroactiveImpactItemCount(3);
        view.setRetroactiveImpactBlockingCount(2);
        view.setRetroactiveImpactPendingCount(3);
        view.setRetroactiveImpactResultHash("a".repeat(64));
        view.setRetroactiveImpactCompletedAt(LocalDateTime.parse("2026-08-25T18:01:00"));
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "case-1", "tenant-1"))
                .thenReturn(Optional.of(view));
        List<MaintenanceRetroactiveImpactSourcePort> ports = requiredPorts();

        var result = service(ports).analyze(input());

        assertEquals("ria-existing", result.analysisId());
        assertEquals(3, result.itemCount());
        verify(commandGateway, never()).sendAndWait(any());
        ports.forEach(port -> verify(port, never()).collect(any()));
    }

    private MaintenanceRetroactiveImpactAnalysisApplicationService service(
            List<MaintenanceRetroactiveImpactSourcePort> ports) {
        return new MaintenanceRetroactiveImpactAnalysisApplicationService(
                commandGateway, maintenanceViewRepository, ports);
    }

    private List<MaintenanceRetroactiveImpactSourcePort> requiredPorts() {
        List<MaintenanceRetroactiveImpactSourcePort> ports = new ArrayList<>();
        for (MaintenanceRetroactiveImpactDomain domain : MaintenanceRetroactiveImpactDomain.values()) {
            MaintenanceRetroactiveImpactSourcePort port = mock(MaintenanceRetroactiveImpactSourcePort.class);
            when(port.sourceDomain()).thenReturn(domain);
            when(port.collect(any())).thenReturn(new SourceEvidence(
                    domain, domain.name() + "-V1", List.of(item(domain))));
            ports.add(port);
        }
        return ports;
    }

    private MaintenanceRetroactiveImpactItem item(MaintenanceRetroactiveImpactDomain domain) {
        MaintenanceRetroactiveImpactType type = switch (domain) {
            case POLICY -> MaintenanceRetroactiveImpactType.SUBSEQUENT_ENDORSEMENT;
            case BILLING -> MaintenanceRetroactiveImpactType.PREMIUM_BILL;
            case PAYMENT -> MaintenanceRetroactiveImpactType.COLLECTION;
            case CLAIM -> MaintenanceRetroactiveImpactType.CLAIM;
        };
        return new MaintenanceRetroactiveImpactItem(
                domain.name() + ":item-1", domain, type, "reference-1", "number-1",
                LocalDateTime.parse("2026-08-20T10:00:00"), "COMPLETED", null, null,
                MaintenanceRetroactiveImpactSeverity.BLOCKING,
                MaintenanceRetroactiveImpactItemStatus.PENDING,
                "追溯影响", "ITEM-V1", Integer.toHexString(domain.ordinal()).repeat(64));
    }

    private MaintenanceView retroactiveCase() {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("case-1");
        view.setPolicyId("policy-1");
        view.setTenantId("tenant-1");
        view.setIndependentCase(true);
        view.setInitializationCompleted(true);
        view.setEffectiveTimeType(EffectiveTimeType.RETROACTIVE);
        view.setSpecificEffectiveDate(LocalDateTime.parse("2026-08-01T00:00:00"));
        return view;
    }

    private MaintenanceRetroactiveImpactAnalysisInput input() {
        return new MaintenanceRetroactiveImpactAnalysisInput(
                "case-1", "operation-1", "operator-1", "tenant-1");
    }
}
