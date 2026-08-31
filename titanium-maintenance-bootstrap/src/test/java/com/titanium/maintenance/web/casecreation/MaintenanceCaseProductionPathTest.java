package com.titanium.maintenance.web.casecreation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.titanium.maintenance.application.command.MaintenanceCaseCommandService;
import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceCaseCreationApplicationService;
import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceFieldDraftApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceEffectApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceEffectScheduleApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceFieldConflictApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceItemWithdrawalApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenancePremiumSettlementApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceRetroactiveImpactAnalysisApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceRetroactivePeriodRecalculationApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceRetroactivePeriodResolutionApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceWorkflowApplicationService;
import com.titanium.maintenance.application.query.MaintenanceCaseQueryApplicationService;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.infrastructure.adapter.PolicyFieldCatalogAdapter;
import com.titanium.maintenance.infrastructure.adapter.PolicyMaintenanceSnapshotAdapter;
import com.titanium.maintenance.infrastructure.adapter.ProductMaintenanceOfferingAdapter;
import com.titanium.maintenance.infrastructure.client.PolicyFieldCatalogClient;
import com.titanium.maintenance.infrastructure.client.PolicyServiceClient;
import com.titanium.maintenance.infrastructure.client.ProductMaintenanceOfferingClient;
import com.titanium.maintenance.port.TenantTimeZonePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;
import com.titanium.maintenance.web.controller.MaintenanceCaseController;
import com.titanium.maintenance.web.handler.MaintenanceExceptionHandler;
import com.titanium.maintenance.web.mapper.MaintenanceCaseQueryWebMapperImpl;
import com.titanium.maintenance.web.security.MaintenanceCaseQueryAccessResolver;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.PolicyMaintenanceSnapshotResponse;
import com.titanium.policy.api.response.PolicySnapshotFieldValueResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCapabilityResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldDescriptorResponse;
import com.titanium.product.api.response.ProductMaintenanceOfferingResolutionResponse;

class MaintenanceCaseProductionPathTest {

    @Test
    void shouldCreateCaseThroughHttpFormalPolicyAdapterAndCommandGateway() throws Exception {
        PolicyServiceClient policyClient = mock(PolicyServiceClient.class);
        ProductMaintenanceOfferingClient productClient = mock(ProductMaintenanceOfferingClient.class);
        MaintenanceItemConfigurationRepository configurationRepository =
                mock(MaintenanceItemConfigurationRepository.class);
        CommandGateway commandGateway = mock(CommandGateway.class);
        when(policyClient.getMaintenanceSnapshot("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(policySnapshot()));
        when(productClient.resolve(
                "product-1", "product-v3", "plan-v8", "EFFECTIVE", "API",
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"), "tenant-1"))
                .thenReturn(ApiResponse.success(productOffering()));
        StoredConfiguration policyInfoConfiguration = storedConfiguration("POLICY_INFO_CHANGE");
        StoredConfiguration beneficiaryConfiguration = storedConfiguration("BENEFICIARY_CHANGE");
        when(configurationRepository.findEffective(
                "tenant-1", "POLICY_INFO_CHANGE", LocalDateTime.parse("2026-08-01T00:00:00")))
                .thenReturn(Optional.of(policyInfoConfiguration));
        when(configurationRepository.findEffective(
                "tenant-1", "BENEFICIARY_CHANGE", LocalDateTime.parse("2026-08-01T00:00:00")))
                .thenReturn(Optional.of(beneficiaryConfiguration));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        MaintenanceCaseCreationApplicationService applicationService = new MaintenanceCaseCreationApplicationService(
                new PolicyMaintenanceSnapshotAdapter(policyClient),
                new ProductMaintenanceOfferingAdapter(productClient), configurationRepository, commandGateway,
                mock(MaintenanceViewRepository.class), mock(TenantTimeZonePort.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        controller(new MaintenanceCaseCommandService(
                                applicationService, mock(MaintenanceFieldDraftApplicationService.class),
                                mock(MaintenanceWorkflowApplicationService.class),
                                mock(MaintenanceEffectApplicationService.class),
                                mock(MaintenanceEffectScheduleApplicationService.class),
                                mock(MaintenanceFieldConflictApplicationService.class),
                                mock(MaintenanceItemWithdrawalApplicationService.class),
                                mock(MaintenancePremiumSettlementApplicationService.class),
                                mock(MaintenanceRetroactiveImpactAnalysisApplicationService.class),
                                mock(MaintenanceRetroactivePeriodRecalculationApplicationService.class),
                                mock(MaintenanceRetroactivePeriodResolutionApplicationService.class))))
                .setControllerAdvice(new MaintenanceExceptionHandler())
                .build();

        MvcResult pending = mockMvc.perform(post("/api/v1/maintenance/cases")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "policyId": "policy-1",
                                  "itemCodes": ["POLICY_INFO_CHANGE", "BENEFICIARY_CHANGE"],
                                  "effectiveTimeType": "IMMEDIATE",
                                  "description": "联系方式变更",
                                  "clientRequestKey": "production-request-1"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maintenanceId").isNotEmpty());
        verify(policyClient).getMaintenanceSnapshot("policy-1", "tenant-1");
        verify(productClient).resolve(
                "product-1", "product-v3", "plan-v8", "EFFECTIVE", "API",
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"), "tenant-1");
        verify(commandGateway, times(4)).send(any());
    }

    @Test
    void shouldRecordFieldDraftThroughFormalPolicyAuthorities() throws Exception {
        PolicyServiceClient policyClient = mock(PolicyServiceClient.class);
        PolicyFieldCatalogClient fieldCatalogClient = mock(PolicyFieldCatalogClient.class);
        CommandGateway commandGateway = mock(CommandGateway.class);
        MaintenanceViewRepository maintenanceViewRepository = mock(MaintenanceViewRepository.class);
        MaintenanceView caseView = new MaintenanceView();
        caseView.setMaintenanceId("maintenance-1");
        caseView.setPolicyId("policy-1");
        caseView.setTenantId("tenant-1");
        caseView.setIndependentCase(true);
        caseView.setInitializationCompleted(true);
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(caseView));
        when(policyClient.getMaintenanceSnapshot("policy-1", "tenant-1"))
                .thenReturn(ApiResponse.success(policySnapshot()));
        when(fieldCatalogClient.getCurrentCatalog(
                "tenant-1", null, null, LocalDate.of(2026, 8, 1)))
                .thenReturn(ApiResponse.success(fieldCatalog()));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        MaintenanceFieldDraftApplicationService fieldDraftService = new MaintenanceFieldDraftApplicationService(
                new PolicyMaintenanceSnapshotAdapter(policyClient),
                new PolicyFieldCatalogAdapter(fieldCatalogClient), commandGateway, maintenanceViewRepository);
        MaintenanceCaseCommandService commandService = new MaintenanceCaseCommandService(
                mock(MaintenanceCaseCreationApplicationService.class), fieldDraftService,
                mock(MaintenanceWorkflowApplicationService.class),
                mock(MaintenanceEffectApplicationService.class),
                mock(MaintenanceEffectScheduleApplicationService.class),
                mock(MaintenanceFieldConflictApplicationService.class),
                mock(MaintenanceItemWithdrawalApplicationService.class),
                mock(MaintenancePremiumSettlementApplicationService.class),
                mock(MaintenanceRetroactiveImpactAnalysisApplicationService.class),
                mock(MaintenanceRetroactivePeriodRecalculationApplicationService.class),
                mock(MaintenanceRetroactivePeriodResolutionApplicationService.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(commandService))
                .setControllerAdvice(new MaintenanceExceptionHandler())
                .build();

        MvcResult pending = mockMvc.perform(put(
                                "/api/v1/maintenance/cases/maintenance-1/items/POLICY_INFO_CHANGE/changes")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "proposals": [{
                                    "fieldCode": "policy.holder.mobile",
                                    "dataType": "TEXT",
                                    "canonicalValue": "13900000000"
                                  }]
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending)).andExpect(status().isNoContent());
        verify(maintenanceViewRepository)
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1");
        verify(policyClient).getMaintenanceSnapshot("policy-1", "tenant-1");
        verify(fieldCatalogClient).getCurrentCatalog(
                "tenant-1", null, null, LocalDate.of(2026, 8, 1));
        verify(commandGateway).send(any());
    }

    private MaintenanceCaseController controller(MaintenanceCaseCommandService commandService) {
        return new MaintenanceCaseController(
                commandService,
                mock(MaintenanceCaseQueryApplicationService.class),
                new MaintenanceCaseQueryWebMapperImpl(),
                mock(MaintenanceCaseQueryAccessResolver.class));
    }

    private PolicyMaintenanceSnapshotResponse policySnapshot() {
        OffsetDateTime capturedAt = OffsetDateTime.parse("2026-08-24T08:00:00Z");
        return new PolicyMaintenanceSnapshotResponse(
                "tenant-1", "policy-1", "P202608240001", "customer-1", "product-1", "product-v3",
                "plan-v8", PolicyStatus.EFFECTIVE, 7L, OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
                "axon-event://policy/tenant-1/policy-1?version=7", "a".repeat(64), capturedAt,
                Map.of("policy.holder.mobile", new PolicySnapshotFieldValueResponse("TEXT", "13800000000")));
    }

    private ProductMaintenanceOfferingResolutionResponse productOffering() {
        return new ProductMaintenanceOfferingResolutionResponse(
                "tenant-1", "product-1", "product-v3", "plan-v8", "offering-1", "offering-v1",
                "b".repeat(64), OffsetDateTime.parse("2026-08-24T08:01:00Z"),
                Set.of("POLICY_INFO_CHANGE", "BENEFICIARY_CHANGE"));
    }

    private PolicyFieldCatalogResponse fieldCatalog() {
        PolicyFieldCapabilityResponse capability = new PolicyFieldCapabilityResponse(
                true, true, true, false, false, "POLICY_INFO_CHANGE");
        PolicyFieldDescriptorResponse mobile = new PolicyFieldDescriptorResponse(
                "policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                "policy.field.holder.mobile", false, null, capability,
                PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE, null);
        return new PolicyFieldCatalogResponse(
                "tenant-1", null, null, LocalDate.of(2026, 8, 1),
                "catalog-v1", "d".repeat(64), List.of(mobile));
    }

    private StoredConfiguration storedConfiguration(String itemCode) {
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                itemCode, "1.0.0", itemCode, MaintenanceItemCategory.BASIC_INFORMATION,
                Set.of(MaintenanceChannel.API), List.of(), List.of(
                        MaintenanceStepDefinition.skipped(1, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(2, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), false);
        MaintenanceItemConfiguration configuration = mock(MaintenanceItemConfiguration.class);
        when(configuration.getConfigurationId()).thenReturn("configuration-" + itemCode);
        when(configuration.getDefinition()).thenReturn(definition);
        when(configuration.getContentHash()).thenReturn("c".repeat(64));
        when(configuration.isEffectiveAt(LocalDateTime.parse("2026-08-01T00:00:00")))
                .thenReturn(true);
        return new StoredConfiguration(configuration, 1L);
    }
}
