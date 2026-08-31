package com.titanium.maintenance.web.casecreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.maintenance.application.command.CreateMaintenanceCaseInput;
import com.titanium.maintenance.application.command.MaintenanceAutomaticReviewInput;
import com.titanium.maintenance.application.command.MaintenanceCaseCommandService;
import com.titanium.maintenance.application.command.MaintenanceEffectApplicationInput;
import com.titanium.maintenance.application.command.MaintenanceEffectScheduleOperationInput;
import com.titanium.maintenance.application.command.MaintenanceManualReviewInput;
import com.titanium.maintenance.application.command.MaintenancePremiumQuoteInput;
import com.titanium.maintenance.application.command.MaintenancePremiumSettlementGateInput;
import com.titanium.maintenance.application.command.MaintenanceRetroactiveImpactAnalysisInput;
import com.titanium.maintenance.application.command.MaintenanceRetroactivePeriodRecalculationInput;
import com.titanium.maintenance.application.command.MaintenanceRetroactivePeriodResolutionInput;
import com.titanium.maintenance.application.command.MaintenanceUnderwritingAssessmentInput;
import com.titanium.maintenance.application.command.MaintenanceWorkflowTaskOperationInput;
import com.titanium.maintenance.application.command.RecordMaintenanceFieldChangesInput;
import com.titanium.maintenance.application.command.RefreshMaintenanceFieldConflictsInput;
import com.titanium.maintenance.application.command.ResolveMaintenanceFieldConflictInput;
import com.titanium.maintenance.application.model.MaintenanceAutomaticReviewResult;
import com.titanium.maintenance.application.model.MaintenanceEffectApplicationResult;
import com.titanium.maintenance.application.model.MaintenanceEffectScheduleResult;
import com.titanium.maintenance.application.model.MaintenanceFieldConflictOperationResult;
import com.titanium.maintenance.application.model.MaintenancePremiumQuoteResult;
import com.titanium.maintenance.application.model.MaintenancePremiumSettlementGateResult;
import com.titanium.maintenance.application.model.MaintenanceRetroactiveImpactAnalysisResult;
import com.titanium.maintenance.application.model.MaintenanceRetroactivePeriodRecalculationResult;
import com.titanium.maintenance.application.model.MaintenanceRetroactivePeriodResolutionResult;
import com.titanium.maintenance.application.model.MaintenanceUnderwritingAssessmentResult;
import com.titanium.maintenance.application.query.MaintenanceCaseQueryApplicationService;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.workflow.MaintenanceAutomaticReviewOutcome;
import com.titanium.maintenance.common.enums.workflow.MaintenanceBillingPostingStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectScheduleStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceFundSettlementType;
import com.titanium.maintenance.common.enums.workflow.MaintenancePremiumQuoteStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodRecalculationStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactivePeriodResolutionStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.web.controller.MaintenanceCaseController;
import com.titanium.maintenance.web.handler.MaintenanceExceptionHandler;
import com.titanium.maintenance.web.mapper.MaintenanceCaseQueryWebMapperImpl;
import com.titanium.maintenance.web.security.MaintenanceCaseQueryAccessResolver;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

class MaintenanceCaseControllerTest {

    private MaintenanceCaseCommandService commandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = mock(MaintenanceCaseCommandService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MaintenanceCaseController(
                        commandService,
                        mock(MaintenanceCaseQueryApplicationService.class),
                        new MaintenanceCaseQueryWebMapperImpl(),
                        mock(MaintenanceCaseQueryAccessResolver.class)))
                .setControllerAdvice(new MaintenanceExceptionHandler())
                .build();
    }

    @Test
    void shouldAnalyzeRetroactiveImpactThroughApiAndWebRoutes() throws Exception {
        when(commandService.analyzeRetroactiveImpact(any())).thenReturn(
                new MaintenanceRetroactiveImpactAnalysisResult(
                        "analysis-1", 1, "operation-analysis-1",
                        MaintenanceRetroactiveImpactAnalysisStatus.COMPLETED,
                        LocalDateTime.parse("2026-08-01T00:00:00"),
                        LocalDateTime.parse("2026-08-25T18:00:00"),
                        4, 2, 4, "a".repeat(64), null, null,
                        LocalDateTime.parse("2026-08-25T18:01:00")));
        String body = "{\"operationId\":\"operation-analysis-1\"}";

        mockMvc.perform(post("/api/v1/maintenance/cases/case-1/retroactive-impact-analysis")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("analysis-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.blockingItemCount").value(2));
        mockMvc.perform(post("/web/v1/maintenance/cases/case-1/retroactive-impact-analysis")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<MaintenanceRetroactiveImpactAnalysisInput> captor =
                ArgumentCaptor.forClass(MaintenanceRetroactiveImpactAnalysisInput.class);
        verify(commandService, times(2)).analyzeRetroactiveImpact(captor.capture());
        assertEquals("tenant-1", captor.getAllValues().getFirst().tenantId());
        assertEquals("api-client-1", captor.getAllValues().getFirst().operatorId());
        assertEquals("operator-1", captor.getAllValues().get(1).operatorId());
    }

    @Test
    void shouldRecalculateRetroactivePeriodsThroughApiAndWebRoutes() throws Exception {
        when(commandService.recalculateRetroactivePeriods(any())).thenReturn(
                new MaintenanceRetroactivePeriodRecalculationResult(
                        "period-recalculation-1", 1, "operation-recalculation-1",
                        MaintenanceRetroactivePeriodRecalculationStatus.REVIEW_REQUIRED,
                        "analysis-1", 1, "a".repeat(64), "product-recalculation-1",
                        MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"), "CNY", 2,
                        "billing-batch-1", "REVIEW_REQUIRED", 1, 1, null, null,
                        LocalDateTime.parse("2026-08-26T10:01:00")));
        String body = "{\"operationId\":\"operation-recalculation-1\"}";

        mockMvc.perform(post("/api/v1/maintenance/cases/case-1/retroactive-period-recalculation")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodRecalculationId").value("period-recalculation-1"))
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.reviewCount").value(1));
        mockMvc.perform(post("/web/v1/maintenance/cases/case-1/retroactive-period-recalculation")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<MaintenanceRetroactivePeriodRecalculationInput> captor =
                ArgumentCaptor.forClass(MaintenanceRetroactivePeriodRecalculationInput.class);
        verify(commandService, times(2)).recalculateRetroactivePeriods(captor.capture());
        assertEquals("api-client-1", captor.getAllValues().getFirst().operatorId());
        assertEquals("operator-1", captor.getAllValues().get(1).operatorId());
    }

    @Test
    void shouldResolveClosedRetroactivePeriodsThroughApiAndWebRoutes() throws Exception {
        when(commandService.resolveRetroactivePeriods(any())).thenReturn(
                new MaintenanceRetroactivePeriodResolutionResult(
                        "resolution-1", "operation-resolution-1",
                        MaintenanceRetroactivePeriodResolutionStatus.COMPLETED,
                        "billing-resolution-1", "billing-batch-1", "a".repeat(64),
                        "2026-08", 1, "b".repeat(64), "结转至当前开放期间",
                        null, null, LocalDateTime.parse("2026-08-26T14:00:00")));
        String body = """
                {
                  "operationId": "operation-resolution-1",
                  "targetAccountingPeriod": "2026-08",
                  "reason": "结转至当前开放期间"
                }
                """;

        mockMvc.perform(post("/api/v1/maintenance/cases/case-1/retroactive-period-resolution")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingResolutionId").value("billing-resolution-1"))
                .andExpect(jsonPath("$.targetAccountingPeriod").value("2026-08"));
        mockMvc.perform(post("/web/v1/maintenance/cases/case-1/retroactive-period-resolution")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<MaintenanceRetroactivePeriodResolutionInput> captor =
                ArgumentCaptor.forClass(MaintenanceRetroactivePeriodResolutionInput.class);
        verify(commandService, times(2)).resolveRetroactivePeriods(captor.capture());
        assertEquals("api-client-1", captor.getAllValues().getFirst().operatorId());
        assertEquals("operator-1", captor.getAllValues().get(1).operatorId());
        assertEquals("2026-08", captor.getAllValues().getFirst().targetAccountingPeriod());
    }

    @Test
    void shouldCreateApiCaseFromTrustedHeaders() throws Exception {
        when(commandService.create(any())).thenReturn(CompletableFuture.completedFuture("maintenance-1"));

        MvcResult pending = mockMvc.perform(post("/api/v1/maintenance/cases")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maintenanceId").value("maintenance-1"));
        CreateMaintenanceCaseInput request = capturedRequest();
        assertEquals("tenant-1", request.tenantId());
        assertEquals("api-client-1", request.operatorId());
        assertEquals(MaintenanceChannel.API, request.source());
    }

    @Test
    void shouldCreateManualCaseAndRejectMissingOperator() throws Exception {
        when(commandService.create(any())).thenReturn(CompletableFuture.completedFuture("maintenance-2"));

        MvcResult pending = mockMvc.perform(post("/web/v1/maintenance/cases")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(pending)).andExpect(status().isCreated());
        assertEquals(MaintenanceChannel.MANUAL, capturedRequest().source());

        mockMvc.perform(post("/api/v1/maintenance/cases")
                        .header("X-Tenant-Id", "tenant-1")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(MaintenanceErrorCode.MAINTENANCE_INVALID_REQUEST.getCode()));
    }

    @Test
    void shouldRecordStructuredFieldDraftWithoutEchoingSensitiveValue() throws Exception {
        when(commandService.recordFieldChanges(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

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

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isNoContent());
        RecordMaintenanceFieldChangesInput input = capturedFieldRequest();
        assertEquals("tenant-1", input.tenantId());
        assertEquals("api-client-1", input.operatorId());
        assertEquals("13900000000", input.proposals().getFirst().canonicalValue());
    }

    @Test
    void shouldApplyEffectFromTrustedApiRoute() throws Exception {
        when(commandService.applyEffect(any())).thenReturn(CompletableFuture.completedFuture(
                new MaintenanceEffectApplicationResult(
                        "effect-request-1", "END-001", 8, "a".repeat(64),
                        LocalDateTime.parse("2026-08-25T10:00:00"))));

        MvcResult pending = mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/tasks/effect-task-1/effect")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-effect-1"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endorsementNo").value("END-001"))
                .andExpect(jsonPath("$.actualPolicyVersion").value(8));
        ArgumentCaptor<MaintenanceEffectApplicationInput> captor =
                ArgumentCaptor.forClass(MaintenanceEffectApplicationInput.class);
        verify(commandService).applyEffect(captor.capture());
        assertEquals(MaintenanceChannel.API, captor.getValue().source());
        assertEquals("api-client-1", captor.getValue().operatorId());
    }

    @Test
    void shouldRefreshFieldConflictsThroughApiAndWebRoutes() throws Exception {
        when(commandService.refreshFieldConflicts(any())).thenReturn(CompletableFuture.completedFuture(
                new MaintenanceFieldConflictOperationResult(
                        "refresh-1", 8, "a".repeat(64), 1, List.of())));
        String body = "{\"operationId\":\"refresh-1\"}";

        MvcResult apiPending = mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/field-conflicts/refresh")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(apiPending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyVersion").value(8))
                .andExpect(jsonPath("$.conflictCount").value(1));

        MvcResult webPending = mockMvc.perform(post(
                                "/web/v1/maintenance/cases/case-1/field-conflicts/refresh")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(webPending)).andExpect(status().isOk());

        ArgumentCaptor<RefreshMaintenanceFieldConflictsInput> captor =
                ArgumentCaptor.forClass(RefreshMaintenanceFieldConflictsInput.class);
        verify(commandService, times(2)).refreshFieldConflicts(captor.capture());
        assertEquals("api-client-1", captor.getAllValues().getFirst().operatorId());
        assertEquals("operator-1", captor.getAllValues().get(1).operatorId());
    }

    @Test
    void shouldResolveFieldConflictWithTypedReentryAndRejectUnknownFields() throws Exception {
        when(commandService.resolveFieldConflict(any())).thenReturn(CompletableFuture.completedFuture(
                new MaintenanceFieldConflictOperationResult(
                        "resolve-1", 8, "b".repeat(64), 0, List.of())));
        String body = """
                {
                  "operationId": "resolve-1",
                  "itemCode": "POLICY_INFO_CHANGE",
                  "objectId": "policy-1",
                  "fieldCode": "policy.holder.mobile",
                  "action": "REENTER",
                  "dataType": "TEXT",
                  "canonicalValue": "13900000000",
                  "reason": "确认客户最新联系方式"
                }
                """;

        MvcResult pending = mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/field-conflicts/resolve")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflictCount").value(0));

        ArgumentCaptor<ResolveMaintenanceFieldConflictInput> captor =
                ArgumentCaptor.forClass(ResolveMaintenanceFieldConflictInput.class);
        verify(commandService).resolveFieldConflict(captor.capture());
        assertEquals(MaintenanceFieldConflictResolutionAction.REENTER, captor.getValue().action());
        assertEquals(PolicyFieldDataType.TEXT, captor.getValue().dataType());
        assertEquals("13900000000", captor.getValue().canonicalValue());

        mockMvc.perform(post("/web/v1/maintenance/cases/case-1/field-conflicts/resolve")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content(body.replace("\"reason\":", "\"unexpected\":true,\"reason\":")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldPauseAndResumeEffectScheduleFromTrustedRoutes() throws Exception {
        LocalDateTime executionAt = LocalDateTime.parse("2026-09-01T02:00:00");
        when(commandService.pauseEffectSchedule(any())).thenReturn(CompletableFuture.completedFuture(
                scheduleResult(MaintenanceEffectScheduleStatus.PAUSED, executionAt)));
        when(commandService.resumeEffectSchedule(any())).thenReturn(CompletableFuture.completedFuture(
                scheduleResult(MaintenanceEffectScheduleStatus.ACTIVE, executionAt)));
        String body = """
                {"operationId":"schedule-operation-1","reason":"人工复核计划"}
                """;

        MvcResult pausePending = mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/effect-schedule/pause")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(pausePending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.tenantZoneId").value("Asia/Shanghai"));

        MvcResult resumePending = mockMvc.perform(post(
                                "/web/v1/maintenance/cases/case-1/effect-schedule/resume")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(resumePending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        List<MaintenanceEffectScheduleOperationInput> inputs = capturedScheduleRequests();
        assertEquals(MaintenanceChannel.API, inputs.get(0).source());
        assertEquals(MaintenanceChannel.MANUAL, inputs.get(1).source());
        assertEquals("人工复核计划", inputs.get(0).reason());
    }

    @Test
    void shouldExecuteEffectScheduleNowFromTrustedRoute() throws Exception {
        when(commandService.executeEffectScheduleNow(any())).thenReturn(new MaintenanceEffectApplicationResult(
                "effect-request-1", "END-002", 9, "b".repeat(64),
                LocalDateTime.parse("2026-08-25T15:00:00")));

        mockMvc.perform(post("/web/v1/maintenance/cases/case-1/effect-schedule/execute-now")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content("{\"operationId\":\"schedule-execute-1\",\"reason\":\"立即执行\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endorsementNo").value("END-002"))
                .andExpect(jsonPath("$.actualPolicyVersion").value(9));

        ArgumentCaptor<MaintenanceEffectScheduleOperationInput> captor = ArgumentCaptor.forClass(
                MaintenanceEffectScheduleOperationInput.class);
        verify(commandService).executeEffectScheduleNow(captor.capture());
        assertEquals(MaintenanceChannel.MANUAL, captor.getValue().source());
        assertEquals("tenant-1", captor.getValue().tenantId());
        assertEquals("operator-1", captor.getValue().operatorId());
    }

    @Test
    void shouldRejectClientSuppliedPolicyIdInFieldDraft() throws Exception {
        mockMvc.perform(put(
                                "/api/v1/maintenance/cases/maintenance-1/items/POLICY_INFO_CHANGE/changes")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "api-client-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "policyId": "policy-2",
                                  "proposals": [{
                                    "fieldCode": "policy.holder.mobile",
                                    "dataType": "TEXT",
                                    "canonicalValue": "13900000000"
                                  }]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(MaintenanceErrorCode.MAINTENANCE_INVALID_REQUEST.getCode()));
    }

    @Test
    void shouldClaimManualTaskFromTrustedRouteContext() throws Exception {
        when(commandService.claimTask(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult pending = mockMvc.perform(post(
                                "/web/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:DATA_ENTRY/claim")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content("{\"operationId\":\"operation-claim-1\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending)).andExpect(status().isNoContent());
        MaintenanceWorkflowTaskOperationInput input = capturedWorkflowRequest();
        assertEquals("tenant-1", input.tenantId());
        assertEquals("operator-1", input.operatorId());
        assertEquals(MaintenanceChannel.MANUAL, input.source());
    }

    @Test
    void shouldAcceptVersionedConditionEvidenceOnlyOnApiRoute() throws Exception {
        when(commandService.decideTaskCondition(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        String body = """
                {
                  "operationId": "operation-condition-1",
                  "ruleVersion": "rule-v2",
                  "inputHash": "%s",
                  "decision": "SKIP",
                  "reason": "低风险无需审核"
                }
                """.formatted("a".repeat(64));

        MvcResult pending = mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:REVIEW/condition-decision")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "rule-engine")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending)).andExpect(status().isNoContent());
        MaintenanceWorkflowTaskOperationInput input = capturedConditionRequest();
        assertEquals(MaintenanceChannel.API, input.source());
        assertEquals(MaintenanceWorkflowConditionDecision.SKIP, input.conditionDecision());

        mockMvc.perform(post(
                                "/web/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:REVIEW/condition-decision")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAcceptManualReviewOnlyOnWebRoute() throws Exception {
        when(commandService.decideReview(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        String body = """
                {
                  "operationId": "operation-review-1",
                  "decision": "REJECT",
                  "policyVersion": "policy-v1",
                  "comment": "材料不一致"
                }
                """;

        MvcResult pending = mockMvc.perform(post(
                                "/web/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:REVIEW/review-decision")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "reviewer-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending)).andExpect(status().isNoContent());
        MaintenanceManualReviewInput input = capturedManualReviewRequest();
        assertEquals(MaintenanceChannel.MANUAL, input.source());
        assertEquals(MaintenanceReviewDecision.REJECT, input.decision());
        assertEquals("reviewer-1", input.operatorId());

        mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:REVIEW/review-decision")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "reviewer-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnManualRequiredOnlyOnAutomaticReviewApiRoute() throws Exception {
        when(commandService.automaticReview(any()))
                .thenReturn(CompletableFuture.completedFuture(new MaintenanceAutomaticReviewResult(
                        MaintenanceAutomaticReviewOutcome.MANUAL_REQUIRED,
                        "APPROVAL_STANDARD", "policy-v1", List.of("IDENTITY_EVIDENCE_INCOMPLETE"))));
        String body = """
                {
                  "operationId": "operation-auto-1",
                  "policyVersion": "policy-v1",
                  "identityVerified": false,
                  "satisfiedMaterialCodes": [],
                  "amountWithinLimit": true,
                  "amountEvidenceHash": "%s",
                  "riskAccepted": true,
                  "riskEvidenceHash": "%s"
                }
                """.formatted("c".repeat(64), "d".repeat(64));

        MvcResult pending = mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:REVIEW/auto-review")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "review-engine")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("MANUAL_REQUIRED"))
                .andExpect(jsonPath("$.reasons[0]").value("IDENTITY_EVIDENCE_INCOMPLETE"));
        MaintenanceAutomaticReviewInput input = capturedAutomaticReviewRequest();
        assertEquals(MaintenanceChannel.API, input.source());
        assertEquals("review-engine", input.operatorId());

        mockMvc.perform(post(
                                "/web/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:REVIEW/auto-review")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "review-engine")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldExposeUnderwritingAssessmentOnApiAndWebRoutes() throws Exception {
        when(commandService.assessUnderwriting(any()))
                .thenReturn(CompletableFuture.completedFuture(new MaintenanceUnderwritingAssessmentResult(
                        "underwriting-1", MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED,
                        "rule-v1", "model-v1", List.of("REVIEW_FIELD:insured.occupation"),
                        "附加条件通过", LocalDateTime.parse("2026-08-25T12:00:00"))));

        MvcResult pending = mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:UNDERWRITING/underwriting-assessment")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "underwriting-client")
                        .contentType("application/json")
                        .content("{\"operationId\":\"operation-underwriting-1\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.underwritingCaseId").value("underwriting-1"))
                .andExpect(jsonPath("$.conclusion").value("CONDITIONAL_APPROVED"))
                .andExpect(jsonPath("$.additionalConditions[0]")
                        .value("REVIEW_FIELD:insured.occupation"));
        MaintenanceUnderwritingAssessmentInput input = capturedUnderwritingRequest();
        assertEquals(MaintenanceChannel.API, input.source());
        assertEquals("underwriting-client", input.operatorId());
    }

    @Test
    void shouldExposeProductPremiumQuoteOnApiAndWebRoutes() throws Exception {
        String resultHash = "a".repeat(64);
        when(commandService.quotePremium(any()))
                .thenReturn(CompletableFuture.completedFuture(new MaintenancePremiumQuoteResult(
                        MaintenancePremiumQuoteStatus.QUOTED,
                        "quote-1",
                        resultHash,
                        "calculation-original-1",
                        "calculation-replacement-1",
                        "pricing-plan-v2",
                        resultHash,
                        "追加应收 88.66 CNY",
                        MaintenanceBalanceDirection.DEBIT,
                        new BigDecimal("88.66"),
                        "CNY",
                        LocalDateTime.parse("2026-08-25T13:00:00"),
                        LocalDateTime.parse("2026-08-26T13:00:00"))));
        String body = """
                {
                  "operationId": "operation-quote-1",
                  "lifecycleType": "ENDORSEMENT",
                  "originalCalculationId": "calculation-original-1",
                  "currency": "CNY",
                  "sumInsured": 500000,
                  "age": 35,
                  "gender": "MALE",
                  "paymentTermYears": 20,
                  "coverageTermYears": 30,
                  "paymentPeriods": 12,
                  "pricingFactors": {"occupationCode": "OCC-01"},
                  "underwritingAdjustments": [{
                    "adjustmentCode": "UW-LOAD-01",
                    "type": "PERCENTAGE",
                    "value": 5,
                    "reason": "职业加费",
                    "ruleVersion": "uw-rule-v2"
                  }],
                  "channelId": "DIRECT",
                  "policyYear": 2,
                  "reason": "职业变更"
                }
                """;

        MvcResult apiPending = mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:FEE/premium-quotes")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "pricing-client")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(apiPending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTED"))
                .andExpect(jsonPath("$.quoteId").value("quote-1"))
                .andExpect(jsonPath("$.quoteVersion").value(resultHash))
                .andExpect(jsonPath("$.direction").value("DEBIT"))
                .andExpect(jsonPath("$.amount").value(88.66));

        MvcResult webPending = mockMvc.perform(post(
                                "/web/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:FEE/premium-quotes")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(webPending)).andExpect(status().isOk());

        List<MaintenancePremiumQuoteInput> inputs = capturedPremiumQuoteRequests(2);
        assertEquals(MaintenanceChannel.API, inputs.get(0).source());
        assertEquals(MaintenanceChannel.MANUAL, inputs.get(1).source());
        assertEquals("calculation-original-1", inputs.get(0).originalCalculationId());
        assertEquals("OCC-01", inputs.get(0).pricingFactors().get("occupationCode"));
        assertEquals("UW-LOAD-01", inputs.get(0).underwritingAdjustments().getFirst().adjustmentCode());
    }

    @Test
    void shouldRejectClientSuppliedPremiumQuoteResultFields() throws Exception {
        mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:FEE/premium-quotes")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "pricing-client")
                        .contentType("application/json")
                        .content("""
                                {
                                  "operationId": "operation-quote-1",
                                  "originalCalculationId": "calculation-original-1",
                                  "reason": "职业变更",
                                  "amount": 88.66
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(MaintenanceErrorCode.MAINTENANCE_INVALID_REQUEST.getCode()));
    }

    @Test
    void shouldExposePremiumSettlementGateOnApiAndWebRoutes() throws Exception {
        LocalDateTime recordedAt = LocalDateTime.parse("2026-08-25T14:00:00");
        when(commandService.settlePremium(any()))
                .thenReturn(CompletableFuture.completedFuture(new MaintenancePremiumSettlementGateResult(
                        MaintenanceWorkflowTaskStatus.WAITING_EXTERNAL,
                        "posting-1",
                        MaintenanceBillingPostingStatus.POSTED,
                        MaintenanceBalanceDirection.DEBIT,
                        new BigDecimal("88.66"),
                        "CNY",
                        MaintenanceFundSettlementType.COLLECTION,
                        MaintenanceFundSettlementStatus.PENDING,
                        null,
                        "payment-1",
                        "PENDING",
                        null,
                        null,
                        recordedAt)));
        String body = """
                {
                  "operationId": "operation-settlement-1",
                  "paymentMethod": "BANK_TRANSFER",
                  "reason": "确认保全费用"
                }
                """;

        MvcResult apiPending = mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:FEE_SETTLEMENT/premium-settlements")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "settlement-client")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(apiPending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("WAITING_EXTERNAL"))
                .andExpect(jsonPath("$.postingId").value("posting-1"))
                .andExpect(jsonPath("$.postingStatus").value("POSTED"))
                .andExpect(jsonPath("$.amount").value(88.66))
                .andExpect(jsonPath("$.fundStatus").value("PENDING"))
                .andExpect(jsonPath("$.orderId").value("payment-1"));

        MvcResult webPending = mockMvc.perform(post(
                                "/web/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:FEE_SETTLEMENT/premium-settlements")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "operator-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(webPending)).andExpect(status().isOk());

        List<MaintenancePremiumSettlementGateInput> inputs = capturedPremiumSettlementRequests(2);
        assertEquals(MaintenanceChannel.API, inputs.get(0).source());
        assertEquals(MaintenanceChannel.MANUAL, inputs.get(1).source());
        assertEquals("BANK_TRANSFER", inputs.get(0).paymentMethod());
        assertEquals("tenant-1", inputs.get(0).tenantId());
    }

    @Test
    void shouldRejectClientSuppliedPremiumSettlementAmount() throws Exception {
        mockMvc.perform(post(
                                "/api/v1/maintenance/cases/case-1/tasks/"
                                        + "case-1:POLICY_INFO_CHANGE:FEE_SETTLEMENT/premium-settlements")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-Operator-Id", "settlement-client")
                        .contentType("application/json")
                        .content("""
                                {
                                  "operationId": "operation-settlement-1",
                                  "paymentMethod": "BANK_TRANSFER",
                                  "reason": "确认保全费用",
                                  "amount": 88.66
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(MaintenanceErrorCode.MAINTENANCE_INVALID_REQUEST.getCode()));

        verify(commandService, never()).settlePremium(any());
    }

    private CreateMaintenanceCaseInput capturedRequest() {
        ArgumentCaptor<CreateMaintenanceCaseInput> captor = ArgumentCaptor
                .forClass(CreateMaintenanceCaseInput.class);
        verify(commandService).create(captor.capture());
        return captor.getValue();
    }

    private RecordMaintenanceFieldChangesInput capturedFieldRequest() {
        ArgumentCaptor<RecordMaintenanceFieldChangesInput> captor = ArgumentCaptor.forClass(
                RecordMaintenanceFieldChangesInput.class);
        verify(commandService).recordFieldChanges(captor.capture());
        return captor.getValue();
    }

    private MaintenanceWorkflowTaskOperationInput capturedWorkflowRequest() {
        ArgumentCaptor<MaintenanceWorkflowTaskOperationInput> captor = ArgumentCaptor.forClass(
                MaintenanceWorkflowTaskOperationInput.class);
        verify(commandService).claimTask(captor.capture());
        return captor.getValue();
    }

    private MaintenanceWorkflowTaskOperationInput capturedConditionRequest() {
        ArgumentCaptor<MaintenanceWorkflowTaskOperationInput> captor = ArgumentCaptor.forClass(
                MaintenanceWorkflowTaskOperationInput.class);
        verify(commandService).decideTaskCondition(captor.capture());
        return captor.getValue();
    }

    private MaintenanceManualReviewInput capturedManualReviewRequest() {
        ArgumentCaptor<MaintenanceManualReviewInput> captor = ArgumentCaptor.forClass(
                MaintenanceManualReviewInput.class);
        verify(commandService).decideReview(captor.capture());
        return captor.getValue();
    }

    private MaintenanceAutomaticReviewInput capturedAutomaticReviewRequest() {
        ArgumentCaptor<MaintenanceAutomaticReviewInput> captor = ArgumentCaptor.forClass(
                MaintenanceAutomaticReviewInput.class);
        verify(commandService).automaticReview(captor.capture());
        return captor.getValue();
    }

    private MaintenanceUnderwritingAssessmentInput capturedUnderwritingRequest() {
        ArgumentCaptor<MaintenanceUnderwritingAssessmentInput> captor = ArgumentCaptor.forClass(
                MaintenanceUnderwritingAssessmentInput.class);
        verify(commandService).assessUnderwriting(captor.capture());
        return captor.getValue();
    }

    private List<MaintenancePremiumQuoteInput> capturedPremiumQuoteRequests(int invocations) {
        ArgumentCaptor<MaintenancePremiumQuoteInput> captor = ArgumentCaptor.forClass(
                MaintenancePremiumQuoteInput.class);
        verify(commandService, times(invocations)).quotePremium(captor.capture());
        return captor.getAllValues();
    }

    private List<MaintenancePremiumSettlementGateInput> capturedPremiumSettlementRequests(int invocations) {
        ArgumentCaptor<MaintenancePremiumSettlementGateInput> captor = ArgumentCaptor.forClass(
                MaintenancePremiumSettlementGateInput.class);
        verify(commandService, times(invocations)).settlePremium(captor.capture());
        return captor.getAllValues();
    }

    private List<MaintenanceEffectScheduleOperationInput> capturedScheduleRequests() {
        ArgumentCaptor<MaintenanceEffectScheduleOperationInput> captor = ArgumentCaptor.forClass(
                MaintenanceEffectScheduleOperationInput.class);
        verify(commandService).pauseEffectSchedule(captor.capture());
        List<MaintenanceEffectScheduleOperationInput> inputs = new ArrayList<>();
        inputs.add(captor.getValue());
        verify(commandService).resumeEffectSchedule(captor.capture());
        inputs.add(captor.getValue());
        return inputs;
    }

    private MaintenanceEffectScheduleResult scheduleResult(
            MaintenanceEffectScheduleStatus status, LocalDateTime executionAt) {
        return new MaintenanceEffectScheduleResult(
                "case-1:effect", EffectiveTimeType.FUTURE, status, "Asia/Shanghai", executionAt,
                0, null, null, null, null);
    }

    private String requestJson() throws Exception {
        return new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(
                new RequestBody("policy-1", "POLICY_INFO_CHANGE", "IMMEDIATE", "联系方式变更", "request-1"));
    }

    private record RequestBody(
            String policyId,
            String maintenanceType,
            String effectiveTimeType,
            String description,
            String clientRequestKey) {
    }
}
