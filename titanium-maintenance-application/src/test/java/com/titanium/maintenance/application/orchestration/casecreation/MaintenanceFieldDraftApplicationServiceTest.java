package com.titanium.maintenance.application.orchestration.casecreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.maintenance.application.command.field.RecordMaintenanceFieldChangesInput.FieldProposalInput;
import com.titanium.maintenance.application.orchestration.configuration.MaintenanceFrozenConfigurationReader;
import com.titanium.maintenance.command.ProposeMaintenanceFieldChangesCommand;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
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
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCapabilityEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
import com.titanium.maintenance.port.ruleengine.MaintenanceConditionRulePort;
import com.titanium.maintenance.port.ruleengine.MaintenanceConditionRulePort.ConditionRuleRequest;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

class MaintenanceFieldDraftApplicationServiceTest {

    private PolicyMaintenanceSnapshotPort policySnapshotPort;
    private PolicyFieldCatalogPort fieldCatalogPort;
    private CommandGateway commandGateway;
    private MaintenanceViewRepository maintenanceViewRepository;
    private MaintenanceCaseItemViewRepository caseItemViewRepository;
    private MaintenanceItemConfigurationRepository configurationRepository;
    private MaintenanceConditionRulePort conditionRulePort;
    private MaintenanceFieldDraftApplicationService service;

    @BeforeEach
    void setUp() {
        policySnapshotPort = mock(PolicyMaintenanceSnapshotPort.class);
        fieldCatalogPort = mock(PolicyFieldCatalogPort.class);
        commandGateway = mock(CommandGateway.class);
        maintenanceViewRepository = mock(MaintenanceViewRepository.class);
        caseItemViewRepository = mock(MaintenanceCaseItemViewRepository.class);
        configurationRepository = mock(MaintenanceItemConfigurationRepository.class);
        conditionRulePort = mock(MaintenanceConditionRulePort.class);
        service = new MaintenanceFieldDraftApplicationService(
                policySnapshotPort, fieldCatalogPort, commandGateway, maintenanceViewRepository,
                caseItemViewRepository,
                new MaintenanceFrozenConfigurationReader(configurationRepository), conditionRulePort);
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.of(caseView()));
    }

    @Test
    void shouldResolveAuthoritiesAndSendStructuredProposalCommand() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        stubFrozenConfiguration("POLICY_INFO_CHANGE", unconditionalFieldRules());

        service.record(request()).join();

        ArgumentCaptor<ProposeMaintenanceFieldChangesCommand> captor = ArgumentCaptor.forClass(
                ProposeMaintenanceFieldChangesCommand.class);
        verify(commandGateway).send(captor.capture());
        ProposeMaintenanceFieldChangesCommand command = captor.getValue();
        assertEquals("maintenance-1", command.id().id());
        assertEquals("13900000000", command.proposals().getFirst().canonicalValue());
        assertEquals(PolicyFieldMaskingPolicy.MOBILE, command.fieldCatalogSnapshot()
                .requireField("policy.holder.mobile").maskingPolicy());
        assertEquals(PolicyFieldValueType.TEXT, command.fieldCatalogSnapshot()
                .requireField("policy.holder.email").valueType());
        verify(policySnapshotPort).capture(new PolicyMaintenanceSnapshotRequest("policy-1", "tenant-1"));
    }

    @Test
    void shouldRecordFieldsForSuspendedPolicyAfterOfferingApprovedCaseCreation() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot(PolicyStatus.SUSPENDED));
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        stubFrozenConfiguration("POLICY_INFO_CHANGE", unconditionalFieldRules());

        service.record(request()).join();

        ArgumentCaptor<ProposeMaintenanceFieldChangesCommand> command =
                ArgumentCaptor.forClass(ProposeMaintenanceFieldChangesCommand.class);
        verify(commandGateway).send(command.capture());
        assertEquals(
                PolicyStatus.SUSPENDED,
                command.getValue().currentPolicySnapshot().policyStatus());
    }

    @Test
    void shouldRejectFieldMissingFromCurrentCatalogBeforeSendingCommand() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        stubFrozenConfiguration("POLICY_INFO_CHANGE", unconditionalFieldRules());
        MaintenanceFieldDraftRequest invalid = new MaintenanceFieldDraftRequest(
                "maintenance-1", "POLICY_INFO_CHANGE",
                List.of(new FieldProposalInput(
                        null, "policy.unknown", PolicyFieldDataType.TEXT, "value")),
                "operator-1", "tenant-1");

        assertThrows(MaintenanceValidationException.class, () -> service.record(invalid));
    }

    @Test
    void shouldRejectFieldWithoutExecutorBeforeSendingCommand() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        stubFrozenConfiguration("POLICY_INFO_CHANGE", unconditionalFieldRules());
        // 邮箱字段可提案但无执行器，改造前会被受理、直到「生效」环节才失败
        MaintenanceFieldDraftRequest notExecutable = new MaintenanceFieldDraftRequest(
                "maintenance-1", "POLICY_INFO_CHANGE",
                List.of(new FieldProposalInput(
                        null, "policy.holder.email", PolicyFieldDataType.TEXT, "holder@example.com")),
                "operator-1", "tenant-1");

        MaintenanceValidationException exception = assertThrows(
                MaintenanceValidationException.class, () -> service.record(notExecutable));

        assertTrue(exception.getMessage().contains("字段尚未开放真实执行: policy.holder.email"));
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldRecordAmountItemDraftUsingProposedFieldAsCatalogEvidence() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        stubFrozenConfiguration("ADDITIONAL_PAYMENT", unconditionalFieldRules());
        // 🔴 ADDITIONAL_PAYMENT 是金额型保全项：Policy 字段目录中没有任何以它自身编码声明 changeTypeCode
        // 的字段，改造前按「仅项目录声明字段」过滤得到空集，草稿在受理环节即被快照的
        // 「字段目录快照不能为空」不变量拒绝，整条链路（草稿→报价→结算→生效）结构性不可达。
        MaintenanceFieldDraftRequest amountRequest = new MaintenanceFieldDraftRequest(
                "maintenance-1", "ADDITIONAL_PAYMENT",
                List.of(new FieldProposalInput(
                        null, "policy.holder.mobile", PolicyFieldDataType.TEXT, "13900000000")),
                "operator-1", "tenant-1");

        service.record(amountRequest).join();

        ArgumentCaptor<ProposeMaintenanceFieldChangesCommand> captor = ArgumentCaptor.forClass(
                ProposeMaintenanceFieldChangesCommand.class);
        verify(commandGateway).send(captor.capture());
        // 并集口径 = 项目录声明字段 ∪ 提案引用字段：本项声明为空集，故快照恰为提案引用的那一个字段；
        // 目录中同租户的其它字段（email）不得被顺带带入——并集只放宽目录覆盖范围，不是放开整份目录。
        assertEquals(
                Set.of("policy.holder.mobile"),
                captor.getValue().fieldCatalogSnapshot().fields().keySet());
    }

    @Test
    void shouldKeepDeclaredItemFieldsInCatalogSnapshotBeyondProposedOnes() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        stubFrozenConfiguration("POLICY_INFO_CHANGE", unconditionalFieldRules());

        service.record(request()).join();

        ArgumentCaptor<ProposeMaintenanceFieldChangesCommand> captor = ArgumentCaptor.forClass(
                ProposeMaintenanceFieldChangesCommand.class);
        verify(commandGateway).send(captor.capture());
        // 字段型保全项的快照仍须是「本项声明的完整字段集」（mobile + email），不得收窄为「本次提案的字段」：
        // 收窄会让 MaintenanceFieldCatalogSnapshot.sameAuthorityAs 依赖的 fields.equals() 语义对存量案件漂移，
        // 幂等早返回随之失效（同一目录采两次得到「不同权威」）。
        assertEquals(
                Set.of("policy.holder.email", "policy.holder.mobile"),
                captor.getValue().fieldCatalogSnapshot().fields().keySet());
    }

    @Test
    void shouldRejectMissingOrUninitializedCaseBeforeReadingPolicy() {
        when(maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        "maintenance-1", "tenant-1"))
                .thenReturn(Optional.empty());

        assertThrows(MaintenanceNotFoundException.class, () -> service.record(request()));

        verifyNoInteractions(policySnapshotPort, fieldCatalogPort, commandGateway);
    }

    // ==================== AC-04：字段条件规则真执行（受理环节、派发命令之前） ====================

    @Test
    void shouldRejectFieldWhoseConditionRuleEvaluatesFalseBeforeSendingCommand() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        stubFrozenConfiguration("POLICY_INFO_CHANGE",
                List.of(conditionalFieldRule("policy.holder.mobile", "RULE_MOBILE_ALLOWED")));
        when(conditionRulePort.isAccepted(any())).thenReturn(false);

        MaintenanceValidationException exception = assertThrows(
                MaintenanceValidationException.class, () -> service.record(request()));

        assertTrue(exception.getMessage().contains("字段不满足条件规则: policy.holder.mobile"));
        // 求值入参逐项落定：规则码取冻结配置（调用方不得自报），值与字段码取本次提案
        ArgumentCaptor<ConditionRuleRequest> captor = ArgumentCaptor.forClass(ConditionRuleRequest.class);
        verify(conditionRulePort).isAccepted(captor.capture());
        assertEquals(new ConditionRuleRequest(
                "tenant-1", "maintenance-1", "RULE_MOBILE_ALLOWED",
                "POLICY_INFO_CHANGE", "policy.holder.mobile", "13900000000"), captor.getValue());
        // 🔴 拒绝必须发生在派发之前：命令未发出，聚合侧从未见过这条提案
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldSendCommandWhenFieldConditionRuleAccepts() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        stubFrozenConfiguration("POLICY_INFO_CHANGE",
                List.of(conditionalFieldRule("policy.holder.mobile", "RULE_MOBILE_ALLOWED")));
        when(conditionRulePort.isAccepted(any())).thenReturn(true);

        service.record(request()).join();

        verify(conditionRulePort).isAccepted(any());
        verify(commandGateway).send(any());
    }

    @Test
    void shouldNotEvaluateConditionRuleWhenFrozenFieldRuleDeclaresNone() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        // 字段已在冻结配置声明（无 conditionRuleCode）⇒ 不受条件规则约束，不得凭空造规则去问规则引擎
        stubFrozenConfiguration("POLICY_INFO_CHANGE", unconditionalFieldRules());

        service.record(request()).join();

        verifyNoInteractions(conditionRulePort);
        verify(commandGateway).send(any());
    }

    @Test
    void shouldFailClosedWhenConditionRulePortIsUnavailable() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        stubFrozenConfiguration("POLICY_INFO_CHANGE",
                List.of(conditionalFieldRule("policy.holder.mobile", "RULE_MOBILE_ALLOWED")));
        MaintenanceRemoteCallException unavailable = new MaintenanceRemoteCallException(
                "规则引擎字段条件规则求值不可达（服务不可达）",
                MaintenanceErrorCode.MAINTENANCE_RULE_ENGINE_REMOTE_ERROR);
        when(conditionRulePort.isAccepted(any())).thenThrow(unavailable);

        MaintenanceRemoteCallException thrown = assertThrows(
                MaintenanceRemoteCallException.class, () -> service.record(request()));

        // 🔴 「问不到规则」不得被改写成「条件不成立」（业务拒绝）或「条件成立」（放行）——原样上抛，不做任何 catch
        assertSame(unavailable, thrown);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void shouldFailClosedWhenFrozenCaseItemViewMissing() {
        when(policySnapshotPort.capture(any())).thenReturn(policySnapshot());
        when(fieldCatalogPort.getCatalog(any())).thenReturn(fieldCatalog());
        when(caseItemViewRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                "tenant-1", "maintenance-1", "POLICY_INFO_CHANGE")).thenReturn(Optional.empty());

        assertThrows(MaintenanceNotFoundException.class, () -> service.record(request()));

        // 冻结证据缺失时不得跳过条件规则求值继续派发（失败关闭），而非「查不到就不校验」
        verifyNoInteractions(conditionRulePort, commandGateway);
    }

    private MaintenanceFieldDraftRequest request() {
        return request("policy.holder.mobile", "13900000000");
    }

    private MaintenanceFieldDraftRequest request(String fieldCode, String canonicalValue) {
        return new MaintenanceFieldDraftRequest(
                "maintenance-1", "POLICY_INFO_CHANGE",
                List.of(new FieldProposalInput(null, fieldCode, PolicyFieldDataType.TEXT, canonicalValue)),
                "operator-1", "tenant-1");
    }

    private MaintenanceView caseView() {
        MaintenanceView view = new MaintenanceView();
        view.setMaintenanceId("maintenance-1");
        view.setPolicyId("policy-1");
        view.setTenantId("tenant-1");
        view.setIndependentCase(true);
        view.setInitializationCompleted(true);
        return view;
    }

    private PolicyMaintenanceSnapshot policySnapshot() {
        return policySnapshot(PolicyStatus.EFFECTIVE);
    }

    private PolicyMaintenanceSnapshot policySnapshot(PolicyStatus status) {
        return new PolicyMaintenanceSnapshot(
                "tenant-1", PolicyId.of("policy-1"), "P202608240001", CustomerId.of("customer-1"),
                "product-1", "product-v1", "plan-v1", status, 7,
                OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
                new MaintenanceSnapshotReference(
                        "axon-event://policy/tenant-1/policy-1?version=7", "a".repeat(64), 7,
                        OffsetDateTime.parse("2026-08-24T08:00:00Z")),
                Map.of("policy.holder.mobile", MaintenanceFieldValue.text("13800000000")));
    }

    private PolicyFieldCatalogEvidence fieldCatalog() {
        // 🔴 手机号在 Policy 真实目录中为 executable（PolicyFieldCatalog.java:75），夹具须与之一致，
        // 否则用例断言的是「提案一个永不具执行能力的字段仍被受理」这一线上不存在的场景。
        PolicyFieldDescriptorEvidence mobile = new PolicyFieldDescriptorEvidence(
                "policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                "policy.field.holder.mobile", false, null,
                new PolicyFieldCapabilityEvidence(true, true, true, true, false, "POLICY_INFO_CHANGE"),
                PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE, null,
                new FieldAnalysisCapability(true, false, false, true));
        // 邮箱在真实目录中为 proposal(...)（PolicyFieldCatalog.java:78），即「可提案、无执行器」。
        PolicyFieldDescriptorEvidence email = new PolicyFieldDescriptorEvidence(
                "policy.holder.email", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                "policy.field.holder.email", false, null,
                new PolicyFieldCapabilityEvidence(true, true, true, false, false, "POLICY_INFO_CHANGE"),
                PolicyFieldSensitivityLevel.PUBLIC, PolicyFieldMaskingPolicy.NONE, null,
                new FieldAnalysisCapability(true, false, false, true));
        return new PolicyFieldCatalogEvidence(
                "tenant-1", null, null, LocalDate.of(2026, 8, 1),
                false, "catalog-v1", "b".repeat(64), List.of(mobile, email));
    }

    /** 无任何条件规则的字段规则（与夹具目录同集：手机号可编辑、邮箱可编辑）。 */
    private List<MaintenanceFieldRule> unconditionalFieldRules() {
        return List.of(
                MaintenanceFieldRule.editable("policy.holder.mobile", true, true),
                MaintenanceFieldRule.editable("policy.holder.email", false, true));
    }

    private MaintenanceFieldRule conditionalFieldRule(String fieldCode, String conditionRuleCode) {
        return new MaintenanceFieldRule(fieldCode, true, true, true, true, conditionRuleCode);
    }

    /**
     * 铺好「案件项读模型 + 冻结配置」两处桩：配置走真实聚合的「草稿→送审→批准→发布」，
     * 内容哈希由 {@code MaintenanceItemConfigurationHasher} 真实计算，读模型三元证据（保全项编码 +
     * 配置版本 + 内容哈希）与配置逐项一致——{@link MaintenanceFrozenConfigurationReader} 的三元核对
     * 因此是真判据，而非被 mock 架空的假校验。
     */
    private void stubFrozenConfiguration(String itemCode, List<MaintenanceFieldRule> fieldRules) {
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                itemCode, "1.0.0", itemCode, MaintenanceItemCategory.BASIC_INFORMATION,
                Set.of(MaintenanceChannel.MANUAL, MaintenanceChannel.API), fieldRules,
                List.of(MaintenanceStepDefinition.required(1, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), false,
                frozenControls());
        LocalDateTime operatedAt = LocalDateTime.parse("2026-08-25T10:00:00");
        MaintenanceItemConfiguration configuration = MaintenanceItemConfiguration.createDraft(
                "configuration-" + itemCode, "tenant-1", definition,
                operatedAt.minusDays(1), operatedAt.plusYears(1), "maker", operatedAt);
        configuration.submitForApproval("maker", operatedAt.plusMinutes(1));
        configuration.approve("checker", operatedAt.plusMinutes(2));
        configuration.publish("publisher", operatedAt.plusMinutes(3),
                new MaintenancePublicationEvidence(
                        "catalog-v1", "f".repeat(64), operatedAt.plusMinutes(3)));
        MaintenanceCaseItemView itemView = new MaintenanceCaseItemView();
        itemView.setItemCode(itemCode);
        itemView.setConfigurationId(configuration.getConfigurationId());
        itemView.setConfigurationVersion(configuration.getDefinition().version());
        itemView.setConfigurationContentHash(configuration.getContentHash());
        when(caseItemViewRepository.findByTenantIdAndMaintenanceIdAndItemCode(
                "tenant-1", "maintenance-1", itemCode)).thenReturn(Optional.of(itemView));
        when(configurationRepository.findById("tenant-1", configuration.getConfigurationId()))
                .thenReturn(Optional.of(new StoredConfiguration(configuration, 1L)));
    }

    private MaintenanceItemControls frozenControls() {
        return new MaintenanceItemControls(
                Set.of(MaintenanceChannelCapability.manualApproval(MaintenanceChannel.MANUAL),
                        new MaintenanceChannelCapability(MaintenanceChannel.API, true)),
                List.of(new MaintenanceMaterialRequirement("IDENTITY", true, null)),
                Set.of(), "APPROVAL_STANDARD", MaintenanceFeeRule.none(),
                new MaintenanceAccessRule(Set.of("maintenance:item:operate"),
                        Set.of("maintenance:item:view")),
                MaintenanceOutputRule.empty());
    }
}
