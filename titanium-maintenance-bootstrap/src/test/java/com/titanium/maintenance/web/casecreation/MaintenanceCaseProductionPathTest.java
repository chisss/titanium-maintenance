package com.titanium.maintenance.web.casecreation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import com.titanium.common.context.RequestContextInterceptor;
import com.titanium.maintenance.application.command.casecreation.MaintenanceCaseCommandService;
import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceCaseCreationApplicationService;
import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceFieldDraftApplicationService;
import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceInFlightAdmissionPolicy;
import com.titanium.maintenance.application.orchestration.configuration.MaintenanceFrozenConfigurationReader;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceEffectApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceEffectScheduleApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceFieldConflictApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceItemWithdrawalApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenancePremiumSettlementApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceRetroactiveImpactAnalysisApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceRetroactivePeriodRecalculationApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceRetroactivePeriodResolutionApplicationService;
import com.titanium.maintenance.application.orchestration.workflow.MaintenanceWorkflowApplicationService;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.infrastructure.adapter.policy.PolicyFieldCatalogAdapter;
import com.titanium.maintenance.infrastructure.adapter.policy.PolicyMaintenanceSnapshotAdapter;
import com.titanium.maintenance.infrastructure.adapter.product.ProductMaintenanceOfferingAdapter;
import com.titanium.maintenance.infrastructure.client.policy.PolicyFieldCatalogClient;
import com.titanium.maintenance.infrastructure.client.policy.PolicyServiceClient;
import com.titanium.maintenance.infrastructure.client.product.ProductMaintenanceOfferingClient;
import com.titanium.maintenance.port.ruleengine.MaintenanceConditionRulePort;
import com.titanium.maintenance.port.tenant.TenantTimeZonePort;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceExclusionRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;
import com.titanium.maintenance.web.controller.cases.MaintenanceCaseCreationController;
import com.titanium.maintenance.web.handler.MaintenanceExceptionHandler;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCapabilityResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldDescriptorResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicySnapshotFieldValueResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceSnapshotResponse;
import com.titanium.product.api.response.maintenance.ProductMaintenanceOfferingResolutionResponse;

class MaintenanceCaseProductionPathTest {

    @Test
    void shouldCreateCaseThroughHttpFormalPolicyAdapterAndCommandGateway() throws Exception {
        PolicyServiceClient policyClient = mock(PolicyServiceClient.class);
        ProductMaintenanceOfferingClient productClient = mock(ProductMaintenanceOfferingClient.class);
        MaintenanceItemConfigurationRepository configurationRepository =
                mock(MaintenanceItemConfigurationRepository.class);
        CommandGateway commandGateway = mock(CommandGateway.class);
        when(policyClient.getMaintenanceSnapshot("policy-1"))
                .thenReturn(ApiResponse.success(policySnapshot()));
        when(productClient.resolve(
                "product-1", "product-v3", "plan-v8", "EFFECTIVE", "API",
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00")))
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
        // 在途冲突门面用**真实实现**而非 mock：本用例是生产路径集成测试，mock 掉闸口等于把新增的
        // 在途/互斥准入从被测路径中摘除。读模型与互斥配置仓储均为空实现 ⇒ 无在途案件，闸口放行。
        MaintenanceInFlightAdmissionPolicy inFlightAdmissionPolicy = new MaintenanceInFlightAdmissionPolicy(
                mock(MaintenanceViewRepository.class), mock(MaintenanceExclusionRepository.class));
        MaintenanceCaseCreationApplicationService applicationService = new MaintenanceCaseCreationApplicationService(
                new PolicyMaintenanceSnapshotAdapter(policyClient),
                new ProductMaintenanceOfferingAdapter(productClient), configurationRepository, commandGateway,
                mock(MaintenanceViewRepository.class), mock(TenantTimeZonePort.class), inFlightAdmissionPolicy);
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
                // m18：显式注册请求上下文拦截器（standaloneSetup 不装配容器里的拦截器），
                // 使 X-Tenant-Id / X-Operator-Id 头经真实路径进入 RequestContextHolder
                .addInterceptors(new RequestContextInterceptor())
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
                .andExpect(jsonPath("$.data.maintenanceId").isNotEmpty());
        verify(policyClient).getMaintenanceSnapshot("policy-1");
        verify(productClient).resolve(
                "product-1", "product-v3", "plan-v8", "EFFECTIVE", "API",
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"));
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
        when(policyClient.getMaintenanceSnapshot("policy-1"))
                .thenReturn(ApiResponse.success(policySnapshot()));
        when(fieldCatalogClient.getCurrentCatalog(null, null, LocalDate.of(2026, 8, 1)))
                .thenReturn(ApiResponse.success(fieldCatalog()));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        // 案件冻结配置证据：item 读模型三元（保全项编码 + 配置版本 + 内容哈希）与配置逐项一致，
        // 字段规则为空集 ⇒ 本路径不产生条件规则求值（conditionRulePort 全程零交互）。
        StoredConfiguration configuration = storedConfiguration("POLICY_INFO_CHANGE");
        MaintenanceItemConfiguration frozen = configuration.configuration();
        MaintenanceCaseItemView itemView = new MaintenanceCaseItemView();
        itemView.setItemCode("POLICY_INFO_CHANGE");
        itemView.setConfigurationId(frozen.getConfigurationId());
        itemView.setConfigurationVersion(frozen.getDefinition().version());
        itemView.setConfigurationContentHash(frozen.getContentHash());
        MaintenanceCaseItemViewRepository caseItemViewRepository = mock(MaintenanceCaseItemViewRepository.class);
        when(caseItemViewRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                "tenant-1", "maintenance-1", "POLICY_INFO_CHANGE"))
                .thenReturn(Optional.of(itemView));
        MaintenanceItemConfigurationRepository configurationRepository =
                mock(MaintenanceItemConfigurationRepository.class);
        when(configurationRepository.findById("tenant-1", frozen.getConfigurationId()))
                .thenReturn(Optional.of(configuration));
        MaintenanceConditionRulePort conditionRulePort = mock(MaintenanceConditionRulePort.class);
        MaintenanceFieldDraftApplicationService fieldDraftService = new MaintenanceFieldDraftApplicationService(
                new PolicyMaintenanceSnapshotAdapter(policyClient),
                new PolicyFieldCatalogAdapter(fieldCatalogClient), commandGateway, maintenanceViewRepository,
                caseItemViewRepository, new MaintenanceFrozenConfigurationReader(configurationRepository),
                conditionRulePort);
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
                // m18：同上，请求头须经拦截器进入请求上下文
                .addInterceptors(new RequestContextInterceptor())
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

        mockMvc.perform(asyncDispatch(pending)).andExpect(status().isOk());
        verify(maintenanceViewRepository)
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1");
        verify(policyClient).getMaintenanceSnapshot("policy-1");
        verify(fieldCatalogClient).getCurrentCatalog(null, null, LocalDate.of(2026, 8, 1));
        verify(commandGateway).send(any());
        // 冻结配置未声明 conditionRuleCode ⇒ 受理路径不得凭空调用规则引擎（无规则可问时不得造规则）
        verifyNoInteractions(conditionRulePort);
    }

    private MaintenanceCaseCreationController controller(MaintenanceCaseCommandService commandService) {
        return new MaintenanceCaseCreationController(commandService);
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
        // 🔴 手机号在 Policy 真实目录中为 executable（PolicyFieldCatalog.java:75）：本用例走的是「成功受理」路径，
        // 夹具须与真实目录一致，否则会被受理环节的可执行性预检拦下、异步不会启动。
        PolicyFieldCapabilityResponse capability = new PolicyFieldCapabilityResponse(
                true, true, true, true, false, "POLICY_INFO_CHANGE");
        PolicyFieldDescriptorResponse mobile = new PolicyFieldDescriptorResponse(
                "policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                "policy.field.holder.mobile", false, null, capability,
                PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE, null,
                // 分析位口径与 Policy 真实目录一致（TEXT 非集合 ⇒ 可分析/可过滤，不可聚合/不可分组）
                new FieldAnalysisCapability(true, false, false, true));
        // fallback=false：本用例走正常命中的受理路径，不涉及「业务日期早于最早版本」的降级口径
        return new PolicyFieldCatalogResponse(
                "tenant-1", null, null, LocalDate.of(2026, 8, 1), false,
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
