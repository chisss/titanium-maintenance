package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.MaintenanceAutomaticReviewInput;
import com.titanium.maintenance.application.command.MaintenanceManualReviewInput;
import com.titanium.maintenance.application.command.MaintenanceUnderwritingAssessmentInput;
import com.titanium.maintenance.application.command.MaintenanceWorkflowTaskOperationInput;
import com.titanium.maintenance.command.ClaimMaintenanceWorkflowTaskCommand;
import com.titanium.maintenance.command.DecideMaintenanceReviewCommand;
import com.titanium.maintenance.command.DecideMaintenanceUnderwritingCommand;
import com.titanium.maintenance.command.DecideMaintenanceWorkflowConditionCommand;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceAutomaticReviewOutcome;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewMode;
import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenancePublicationEvidence;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.configuration.control.MaintenanceAccessRule;
import com.titanium.maintenance.configuration.control.MaintenanceChannelCapability;
import com.titanium.maintenance.configuration.control.MaintenanceFeeRule;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.configuration.control.MaintenanceMaterialRequirement;
import com.titanium.maintenance.configuration.control.MaintenanceOutputRule;
import com.titanium.maintenance.port.MaintenanceUnderwritingPort;
import com.titanium.maintenance.port.MaintenanceUnderwritingPort.AssessmentFact;
import com.titanium.maintenance.port.MaintenanceUnderwritingPort.AssessmentRequest;
import com.titanium.maintenance.port.PolicyServicePort;
import com.titanium.maintenance.port.ProductMaintenancePremiumQuotePort;
import com.titanium.maintenance.port.ProductSurrenderValuePort;
import com.titanium.maintenance.port.TenantTimeZonePort;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceFieldChangeViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceSnapshotViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceWorkflowTaskViewRepository;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceFieldChangeView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.query.view.MaintenanceWorkflowTaskView;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;

class MaintenanceWorkflowApplicationServiceTest {

    private static final String CASE_ID = "case-1";
    private static final String TASK_ID = "case-1:POLICY_INFO_CHANGE:DATA_ENTRY";

    private CommandGateway commandGateway;
    private MaintenanceViewRepository maintenanceViewRepository;
    private MaintenanceWorkflowTaskViewRepository taskViewRepository;
    private MaintenanceCaseItemViewRepository caseItemViewRepository;
    private MaintenanceFieldChangeViewRepository fieldChangeViewRepository;
    private MaintenanceItemConfigurationRepository configurationRepository;
    private MaintenanceUnderwritingPort underwritingPort;
    private MaintenanceSnapshotViewRepository snapshotViewRepository;
    private ProductMaintenancePremiumQuotePort premiumQuotePort;
    private MaintenanceWorkflowApplicationService service;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        maintenanceViewRepository = mock(MaintenanceViewRepository.class);
        taskViewRepository = mock(MaintenanceWorkflowTaskViewRepository.class);
        caseItemViewRepository = mock(MaintenanceCaseItemViewRepository.class);
        fieldChangeViewRepository = mock(MaintenanceFieldChangeViewRepository.class);
        configurationRepository = mock(MaintenanceItemConfigurationRepository.class);
        underwritingPort = mock(MaintenanceUnderwritingPort.class);
        snapshotViewRepository = mock(MaintenanceSnapshotViewRepository.class);
        premiumQuotePort = mock(ProductMaintenancePremiumQuotePort.class);
        service = new MaintenanceWorkflowApplicationService(
                commandGateway, maintenanceViewRepository, taskViewRepository,
                caseItemViewRepository, fieldChangeViewRepository, configurationRepository,
                new MaintenanceReviewPolicyEvaluator(), underwritingPort,
                snapshotViewRepository, premiumQuotePort,
                mock(PolicyServicePort.class), mock(ProductSurrenderValuePort.class),
                mock(TenantTimeZonePort.class));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void shouldResolveTenantScopedTaskBeforeSendingClaim() {
        visibleTask("tenant-1", MaintenanceStepType.DATA_ENTRY);

        service.claim(input(MaintenanceChannel.MANUAL)).join();

        ArgumentCaptor<ClaimMaintenanceWorkflowTaskCommand> captor =
                ArgumentCaptor.forClass(ClaimMaintenanceWorkflowTaskCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals(CASE_ID, captor.getValue().id().id());
        assertEquals(TASK_ID, captor.getValue().taskId());
        verify(taskViewRepository).findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", CASE_ID, TASK_ID);
    }

    @Test
    void shouldHideCrossTenantCaseBeforeSendingCommand() {
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        CASE_ID, "tenant-1"))
                .thenReturn(Optional.empty());

        assertThrows(MaintenanceNotFoundException.class,
                () -> service.claim(input(MaintenanceChannel.MANUAL)));
        verify(commandGateway, never()).send(any());
        verify(taskViewRepository, never()).findByTenantIdAndMaintenanceIdAndTaskId(
                any(), any(), any());
    }

    @Test
    void shouldRejectManualValidationCompletion() {
        visibleTask("tenant-1", MaintenanceStepType.VALIDATION);

        assertThrows(MaintenanceValidationException.class,
                () -> service.complete(input(MaintenanceChannel.MANUAL)));
        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldAllowApiConditionEvidence() {
        visibleTask("tenant-1", MaintenanceStepType.REVIEW);
        MaintenanceWorkflowTaskOperationInput input = new MaintenanceWorkflowTaskOperationInput(
                CASE_ID, TASK_ID, "operation-1", "rule-v2", "a".repeat(64),
                null, "低风险无需审核", MaintenanceWorkflowConditionDecision.SKIP,
                "rule-engine", "tenant-1", MaintenanceChannel.API);

        service.decideCondition(input).join();

        ArgumentCaptor<DecideMaintenanceWorkflowConditionCommand> captor =
                ArgumentCaptor.forClass(DecideMaintenanceWorkflowConditionCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals("rule-v2", captor.getValue().ruleVersion());
        assertEquals(MaintenanceWorkflowConditionDecision.SKIP, captor.getValue().decision());
    }

    @Test
    void shouldRejectManualReviewByCaseCreator() {
        MaintenanceItemConfiguration configuration = publishedConfiguration();
        visibleReviewTask(configuration, MaintenanceChannel.MANUAL,
                MaintenanceWorkflowTaskStatus.IN_PROGRESS, "maker-1", "maker-1");
        MaintenanceManualReviewInput input = new MaintenanceManualReviewInput(
                CASE_ID, reviewTaskId(), "operation-review", MaintenanceReviewDecision.APPROVE,
                "policy-v1", "审核通过", "maker-1", "tenant-1", MaintenanceChannel.MANUAL);

        assertThrows(MaintenanceValidationException.class, () -> service.decideReview(input));
        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldSendManualReviewUsingFrozenApprovalPolicy() {
        MaintenanceItemConfiguration configuration = publishedConfiguration();
        visibleReviewTask(configuration, MaintenanceChannel.MANUAL,
                MaintenanceWorkflowTaskStatus.IN_PROGRESS, "reviewer-1", "maker-1");
        MaintenanceManualReviewInput input = new MaintenanceManualReviewInput(
                CASE_ID, reviewTaskId(), "operation-review", MaintenanceReviewDecision.REJECT,
                "policy-v1", "材料不一致", "reviewer-1", "tenant-1", MaintenanceChannel.MANUAL);

        service.decideReview(input).join();

        ArgumentCaptor<DecideMaintenanceReviewCommand> captor =
                ArgumentCaptor.forClass(DecideMaintenanceReviewCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals("APPROVAL_STANDARD", captor.getValue().evidence().policyCode());
        assertEquals(MaintenanceReviewDecision.REJECT, captor.getValue().evidence().decision());
    }

    @Test
    void shouldApproveAutomaticReviewOnlyAfterSevenGatesPass() {
        MaintenanceItemConfiguration configuration = publishedConfiguration();
        visibleReviewTask(configuration, MaintenanceChannel.API,
                MaintenanceWorkflowTaskStatus.READY, null, "maker-1");

        var result = service.automaticReview(automaticInput(true, "a".repeat(64))).join();

        assertEquals(MaintenanceAutomaticReviewOutcome.APPROVED, result.outcome());
        ArgumentCaptor<DecideMaintenanceReviewCommand> captor =
                ArgumentCaptor.forClass(DecideMaintenanceReviewCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals(7, captor.getValue().evidence().gates().size());
    }

    @Test
    void shouldLeaveTaskForManualReviewWhenAutomaticEvidenceIsMissing() {
        MaintenanceItemConfiguration configuration = publishedConfiguration();
        visibleReviewTask(configuration, MaintenanceChannel.API,
                MaintenanceWorkflowTaskStatus.READY, null, "maker-1");

        var result = service.automaticReview(automaticInput(false, null)).join();

        assertEquals(MaintenanceAutomaticReviewOutcome.MANUAL_REQUIRED, result.outcome());
        assertTrue(result.reasons().contains("IDENTITY_EVIDENCE_INCOMPLETE"));
        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldNotOverwriteCompletedManualReviewWithAutomaticDecision() {
        MaintenanceItemConfiguration configuration = publishedConfiguration();
        visibleReviewTask(configuration, MaintenanceChannel.API,
                MaintenanceWorkflowTaskStatus.COMPLETED, "reviewer-1", "maker-1");

        var result = service.automaticReview(automaticInput(true, "a".repeat(64))).join();

        assertEquals(MaintenanceAutomaticReviewOutcome.MANUAL_REQUIRED, result.outcome());
        assertTrue(result.reasons().contains("TASK_REQUIRES_MANUAL_HANDLING"));
        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldAllowSameAutomaticOperationToRetryAfterProjectionCompletes() {
        MaintenanceItemConfiguration configuration = publishedConfiguration();
        visibleReviewTask(configuration, MaintenanceChannel.API,
                MaintenanceWorkflowTaskStatus.COMPLETED, null, "maker-1");

        var result = service.automaticReview(automaticInput(true, "a".repeat(64))).join();

        assertEquals(MaintenanceAutomaticReviewOutcome.APPROVED, result.outcome());
        verify(commandGateway).send(any(DecideMaintenanceReviewCommand.class));
    }

    @Test
    void shouldFailClosedWhenFrozenConfigurationHashDoesNotMatch() {
        MaintenanceItemConfiguration configuration = publishedConfiguration();
        MaintenanceCaseItemView itemView = visibleReviewTask(
                configuration, MaintenanceChannel.API,
                MaintenanceWorkflowTaskStatus.READY, null, "maker-1");
        itemView.setConfigurationContentHash("0".repeat(64));

        assertThrows(MaintenanceValidationException.class,
                () -> service.automaticReview(automaticInput(true, "a".repeat(64))));
        verify(commandGateway, never()).send(any());
    }

    @Test
    void shouldBuildUnderwritingRequestFromFrozenFactsAndSendDecision() {
        MaintenanceItemConfiguration configuration = publishedUnderwritingConfiguration();
        visibleUnderwritingTask(configuration);
        when(underwritingPort.assess(any())).thenAnswer(invocation -> {
            AssessmentRequest request = invocation.getArgument(0);
            return new AssessmentFact(
                    "underwriting-1", request.idempotencyKey(), request.payloadHash(),
                    "rule-v1", "model-v1", MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED,
                    List.of("REVIEW_FIELD:insured.occupation"), "附加条件通过",
                    LocalDateTime.parse("2026-08-25T12:00:00"));
        });

        var result = service.assessUnderwriting(underwritingInput()).join();

        assertEquals(MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED, result.conclusion());
        ArgumentCaptor<DecideMaintenanceUnderwritingCommand> captor =
                ArgumentCaptor.forClass(DecideMaintenanceUnderwritingCommand.class);
        verify(commandGateway).send(captor.capture());
        assertEquals("underwriting-1", captor.getValue().evidence().underwritingCaseId());
        assertEquals("rule-v1", captor.getValue().evidence().ruleVersion());
    }

    @Test
    void shouldNotWriteTaskFactWhenUnderwritingTimesOut() {
        MaintenanceItemConfiguration configuration = publishedUnderwritingConfiguration();
        visibleUnderwritingTask(configuration);
        when(underwritingPort.assess(any())).thenThrow(new RuntimeException("timeout"));

        assertThrows(RuntimeException.class, () -> service.assessUnderwriting(underwritingInput()));

        verify(commandGateway, never()).send(any(DecideMaintenanceUnderwritingCommand.class));
    }

    private void visibleTask(String tenantId, MaintenanceStepType stepType) {
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        CASE_ID, tenantId))
                .thenReturn(Optional.of(new MaintenanceView()));
        MaintenanceWorkflowTaskView task = new MaintenanceWorkflowTaskView();
        task.setTaskId(TASK_ID);
        task.setMaintenanceId(CASE_ID);
        task.setTenantId(tenantId);
        task.setStepType(stepType);
        when(taskViewRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                tenantId, CASE_ID, TASK_ID))
                .thenReturn(Optional.of(task));
    }

    private MaintenanceWorkflowTaskOperationInput input(MaintenanceChannel source) {
        return new MaintenanceWorkflowTaskOperationInput(
                CASE_ID, TASK_ID, "operation-1", "evidence-v1", "a".repeat(64),
                "PASSED", "处理完成", null, "operator-1", "tenant-1", source);
    }

    private MaintenanceCaseItemView visibleReviewTask(
            MaintenanceItemConfiguration configuration,
            MaintenanceChannel source,
            MaintenanceWorkflowTaskStatus status,
            String assignedTo,
            String createdBy) {
        MaintenanceView caseView = new MaintenanceView();
        caseView.setMaintenanceId(CASE_ID);
        caseView.setTenantId("tenant-1");
        caseView.setSource(source);
        caseView.setStatus(MaintenanceStatus.PENDING);
        caseView.setCreatedBy(createdBy);
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        CASE_ID, "tenant-1"))
                .thenReturn(Optional.of(caseView));

        MaintenanceWorkflowTaskView taskView = new MaintenanceWorkflowTaskView();
        taskView.setTaskId(reviewTaskId());
        taskView.setMaintenanceId(CASE_ID);
        taskView.setTenantId("tenant-1");
        taskView.setItemCode("POLICY_INFO_CHANGE");
        taskView.setStepType(MaintenanceStepType.REVIEW);
        taskView.setStatus(status);
        taskView.setAssignedTo(assignedTo);
        if (status == MaintenanceWorkflowTaskStatus.COMPLETED && assignedTo == null) {
            taskView.setReviewMode(MaintenanceReviewMode.AUTOMATIC);
            taskView.setLastOperationId("operation-auto");
        }
        when(taskViewRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", CASE_ID, reviewTaskId()))
                .thenReturn(Optional.of(taskView));

        MaintenanceCaseItemView itemView = new MaintenanceCaseItemView();
        itemView.setMaintenanceId(CASE_ID);
        itemView.setItemCode("POLICY_INFO_CHANGE");
        itemView.setConfigurationId(configuration.getConfigurationId());
        itemView.setConfigurationVersion(configuration.getDefinition().version());
        itemView.setConfigurationContentHash(configuration.getContentHash());
        itemView.setOfferingId("offering-1");
        itemView.setOfferingVersion("offering-v1");
        itemView.setOfferingContentHash("e".repeat(64));
        when(caseItemViewRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                "tenant-1", CASE_ID, "POLICY_INFO_CHANGE"))
                .thenReturn(Optional.of(itemView));
        when(configurationRepository.findById("tenant-1", configuration.getConfigurationId()))
                .thenReturn(Optional.of(new StoredConfiguration(configuration, 1L)));
        return itemView;
    }

    private MaintenanceAutomaticReviewInput automaticInput(
            boolean identityVerified,
            String identityEvidenceHash) {
        return new MaintenanceAutomaticReviewInput(
                CASE_ID, reviewTaskId(), "operation-auto", "policy-v1",
                identityVerified, identityEvidenceHash, List.of("IDENTITY"), "b".repeat(64),
                true, "c".repeat(64), true, "d".repeat(64),
                "review-engine", "tenant-1", MaintenanceChannel.API);
    }

    private MaintenanceUnderwritingAssessmentInput underwritingInput() {
        return new MaintenanceUnderwritingAssessmentInput(
                CASE_ID, underwritingTaskId(), "operation-underwriting-1",
                "underwriting-client", "tenant-1", MaintenanceChannel.API);
    }

    private void visibleUnderwritingTask(MaintenanceItemConfiguration configuration) {
        MaintenanceView caseView = new MaintenanceView();
        caseView.setMaintenanceId(CASE_ID);
        caseView.setTenantId("tenant-1");
        caseView.setPolicyId("policy-1");
        caseView.setPolicyBaselineVersion(7L);
        caseView.setProductId("product-1");
        caseView.setProductVersion("product-v3");
        caseView.setPlanVersion("plan-v2");
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        CASE_ID, "tenant-1"))
                .thenReturn(Optional.of(caseView));

        MaintenanceWorkflowTaskView taskView = new MaintenanceWorkflowTaskView();
        taskView.setTaskId(underwritingTaskId());
        taskView.setMaintenanceId(CASE_ID);
        taskView.setTenantId("tenant-1");
        taskView.setItemCode("POLICY_INFO_CHANGE");
        taskView.setSequence(1);
        taskView.setStepType(MaintenanceStepType.UNDERWRITING);
        taskView.setMode(MaintenanceStepMode.REQUIRED);
        taskView.setStatus(MaintenanceWorkflowTaskStatus.READY);
        when(taskViewRepository.findByTenantIdAndMaintenanceIdAndTaskId(
                "tenant-1", CASE_ID, underwritingTaskId()))
                .thenReturn(Optional.of(taskView));

        MaintenanceCaseItemView itemView = new MaintenanceCaseItemView();
        itemView.setMaintenanceId(CASE_ID);
        itemView.setItemCode("POLICY_INFO_CHANGE");
        itemView.setConfigurationId(configuration.getConfigurationId());
        itemView.setConfigurationVersion(configuration.getDefinition().version());
        itemView.setConfigurationContentHash(configuration.getContentHash());
        when(caseItemViewRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                "tenant-1", CASE_ID, "POLICY_INFO_CHANGE"))
                .thenReturn(Optional.of(itemView));
        when(configurationRepository.findById("tenant-1", configuration.getConfigurationId()))
                .thenReturn(Optional.of(new StoredConfiguration(configuration, 1L)));

        MaintenanceFieldChangeView field = new MaintenanceFieldChangeView();
        field.setObjectId("insured-1");
        field.setFieldCode("insured.occupation");
        field.setDataType(PolicyFieldDataType.TEXT);
        field.setBaseValue("1");
        field.setProposedValue("4");
        field.setChangeTypeCode("UW_CONDITIONAL_OCCUPATION");
        field.setConflictStatus(MaintenanceFieldConflictStatus.NONE);
        when(fieldChangeViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCodeOrderByFieldCodeAscObjectIdAsc(
                        "tenant-1", CASE_ID, "POLICY_INFO_CHANGE"))
                .thenReturn(List.of(field));
    }

    private MaintenanceItemConfiguration publishedConfiguration() {
        MaintenanceItemControls controls = new MaintenanceItemControls(
                Set.of(MaintenanceChannelCapability.manualApproval(MaintenanceChannel.MANUAL),
                        new MaintenanceChannelCapability(MaintenanceChannel.API, true)),
                List.of(new MaintenanceMaterialRequirement("IDENTITY", true, null)),
                Set.of(), "APPROVAL_STANDARD", MaintenanceFeeRule.none(),
                new MaintenanceAccessRule(Set.of("maintenance:item:operate"),
                        Set.of("maintenance:item:view")),
                MaintenanceOutputRule.empty());
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                "POLICY_INFO_CHANGE", "1.0.0", "保单基本信息变更",
                MaintenanceItemCategory.BASIC_INFORMATION,
                Set.of(MaintenanceChannel.MANUAL, MaintenanceChannel.API), List.of(),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.REVIEW),
                        MaintenanceStepDefinition.required(2, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), true, controls);
        LocalDateTime operatedAt = LocalDateTime.parse("2026-08-25T10:00:00");
        MaintenanceItemConfiguration configuration = MaintenanceItemConfiguration.createDraft(
                "configuration-1", "tenant-1", definition,
                operatedAt.minusDays(1), operatedAt.plusYears(1), "maker", operatedAt);
        configuration.submitForApproval("maker", operatedAt.plusMinutes(1));
        configuration.approve("checker", operatedAt.plusMinutes(2));
        configuration.publish("publisher", operatedAt.plusMinutes(3),
                new MaintenancePublicationEvidence(
                        "catalog-v1", "f".repeat(64), operatedAt.plusMinutes(3)));
        return configuration;
    }

    private MaintenanceItemConfiguration publishedUnderwritingConfiguration() {
        MaintenanceItemControls controls = new MaintenanceItemControls(
                Set.of(MaintenanceChannelCapability.manualApproval(MaintenanceChannel.MANUAL),
                        new MaintenanceChannelCapability(MaintenanceChannel.API, true)),
                List.of(), Set.of(), "APPROVAL_STANDARD", MaintenanceFeeRule.none(),
                new MaintenanceAccessRule(Set.of("maintenance:item:operate"),
                        Set.of("maintenance:item:view")),
                MaintenanceOutputRule.empty());
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                "POLICY_INFO_CHANGE", "1.0.0", "保单基本信息变更",
                MaintenanceItemCategory.BASIC_INFORMATION,
                Set.of(MaintenanceChannel.MANUAL, MaintenanceChannel.API), List.of(),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.UNDERWRITING),
                        MaintenanceStepDefinition.required(2, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), true, controls);
        LocalDateTime operatedAt = LocalDateTime.parse("2026-08-25T10:00:00");
        MaintenanceItemConfiguration configuration = MaintenanceItemConfiguration.createDraft(
                "configuration-underwriting", "tenant-1", definition,
                operatedAt.minusDays(1), operatedAt.plusYears(1), "maker", operatedAt);
        configuration.submitForApproval("maker", operatedAt.plusMinutes(1));
        configuration.approve("checker", operatedAt.plusMinutes(2));
        configuration.publish("publisher", operatedAt.plusMinutes(3),
                new MaintenancePublicationEvidence(
                        "catalog-v1", "f".repeat(64), operatedAt.plusMinutes(3)));
        return configuration;
    }

    private String reviewTaskId() {
        return CASE_ID + ":POLICY_INFO_CHANGE:REVIEW";
    }

    private String underwritingTaskId() {
        return CASE_ID + ":POLICY_INFO_CHANGE:UNDERWRITING";
    }
}
