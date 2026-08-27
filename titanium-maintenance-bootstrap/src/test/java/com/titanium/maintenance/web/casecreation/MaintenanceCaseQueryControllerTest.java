package com.titanium.maintenance.web.casecreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.titanium.maintenance.application.command.MaintenanceCaseCommandService;
import com.titanium.maintenance.application.query.MaintenanceCaseQueryApplicationService;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.query.query.MaintenanceCaseSearchCriteria;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.EffectCompensationQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.FieldChangeQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.SnapshotSetQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowTaskQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCasePageQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCasePageQueryResult.MaintenanceCaseSummaryQueryResult;
import com.titanium.maintenance.web.controller.MaintenanceCaseController;
import com.titanium.maintenance.web.handler.MaintenanceExceptionHandler;
import com.titanium.maintenance.web.mapper.MaintenanceCaseQueryWebMapper;
import com.titanium.maintenance.web.security.MaintenanceCaseQueryAccessResolver;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;

class MaintenanceCaseQueryControllerTest {

    private MaintenanceCaseQueryApplicationService queryService;
    private MaintenanceCaseQueryAccessResolver accessResolver;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(MaintenanceCaseQueryApplicationService.class);
        accessResolver = mock(MaintenanceCaseQueryAccessResolver.class);
        MaintenanceCaseController controller = new MaintenanceCaseController(
                mock(MaintenanceCaseCommandService.class), queryService,
                new MaintenanceCaseQueryWebMapper(), accessResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MaintenanceExceptionHandler())
                .build();
    }

    @Test
    void shouldSearchIndependentCasesWithStructuredFilters() throws Exception {
        MaintenanceCaseSummaryQueryResult summary = new MaintenanceCaseSummaryQueryResult(
                "case-1", "policy-1", "P202608240001", "customer-1",
                List.of("POLICY_INFO_CHANGE"), MaintenanceChannel.MANUAL, MaintenanceStatus.PENDING,
                "operator-1", LocalDateTime.parse("2026-08-24T10:00:00"),
                LocalDateTime.parse("2026-08-24T10:05:00"));
        when(queryService.search(any(), any()))
                .thenReturn(new MaintenanceCasePageQueryResult(List.of(summary), 1, 0, 20, 1));

        mockMvc.perform(get("/web/v1/maintenance/cases")
                        .header("X-Tenant-Id", "tenant-1")
                        .param("itemCode", "POLICY_INFO_CHANGE")
                        .param("source", "MANUAL")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.list[0].caseId").value("case-1"))
                .andExpect(jsonPath("$.list[0].effectStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.list[0].itemCodes[0]").value("POLICY_INFO_CHANGE"));
        ArgumentCaptor<MaintenanceCaseSearchCriteria> captor =
                ArgumentCaptor.forClass(MaintenanceCaseSearchCriteria.class);
        verify(queryService).search(org.mockito.ArgumentMatchers.eq("tenant-1"), captor.capture());
        assertEquals(MaintenanceChannel.MANUAL, captor.getValue().source());
        assertEquals("POLICY_INFO_CHANGE", captor.getValue().itemCode());
    }

    @Test
    void shouldReturnMaskedDetailWithoutSensitiveAuthority() throws Exception {
        when(accessResolver.sensitiveDetailsVisible()).thenReturn(false);
        when(queryService.findDetail("tenant-1", "case-1", false)).thenReturn(detail());

        mockMvc.perform(get("/api/v1/maintenance/cases/case-1")
                        .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value("case-1"))
                .andExpect(jsonPath("$.effectStatus").value("FAILED"))
                .andExpect(jsonPath("$.effectCompensation.required").value(true))
                .andExpect(jsonPath("$.effectCompensation.compensationId").value("compensation-1"))
                .andExpect(jsonPath("$.effectCompensation.requestId").value("request-1"))
                .andExpect(jsonPath("$.effectCompensation.endorsementNo").value("END-20260825-001"))
                .andExpect(jsonPath("$.effectCompensation.actualPolicyVersion").value(8))
                .andExpect(jsonPath("$.effectCompensation.failureReason").value("案件回执写入失败"))
                .andExpect(jsonPath("$.workflowTasks[0].stepType").value("DATA_ENTRY"))
                .andExpect(jsonPath("$.workflowTasks[0].status").value("READY"))
                .andExpect(jsonPath("$.fieldChanges[0].baseValue").value("138****0000"))
                .andExpect(jsonPath("$.fieldChanges[0].proposedValue").value("139****0000"));
        verify(queryService).findDetail("tenant-1", "case-1", false);
    }

    private MaintenanceCaseDetailQueryResult detail() {
        FieldChangeQueryResult field = new FieldChangeQueryResult(
                "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile", "policy.field.holder.mobile",
                PolicyFieldDataType.TEXT, "138****0000", "138****0000", "139****0000", null,
                MaintenanceFieldConflictStatus.NONE, null,
                PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE,
                "POLICY_INFO_CHANGE");
        WorkflowTaskQueryResult task = new WorkflowTaskQueryResult(
                "case-1:POLICY_INFO_CHANGE:DATA_ENTRY", "POLICY_INFO_CHANGE", 0, 2,
                MaintenanceStepType.DATA_ENTRY, MaintenanceStepMode.REQUIRED, null,
                MaintenanceWorkflowTaskStatus.READY, null, 0, null, null, null, null);
        return new MaintenanceCaseDetailQueryResult(
                "case-1", "policy-1", "P202608240001", "customer-1",
                "product-1", "product-v1", "plan-v1", 7L,
                "2026-08-01T00:00:00+08:00", MaintenanceChannel.API,
                MaintenanceStatus.PENDING, MaintenanceEffectStatus.FAILED,
                new EffectCompensationQueryResult(
                        true, "compensation-1", "request-1", "END-20260825-001", 8L,
                        "a".repeat(64), "案件回执写入失败",
                        LocalDateTime.parse("2026-08-25T16:00:00"), null, null),
                EffectiveTimeType.IMMEDIATE, null,
                "联系方式变更", "api-client-1", LocalDateTime.parse("2026-08-24T10:00:00"),
                "api-client-1", LocalDateTime.parse("2026-08-24T10:05:00"),
                List.of(), List.of(task), List.of(field), new SnapshotSetQueryResult(null, null, null));
    }
}
