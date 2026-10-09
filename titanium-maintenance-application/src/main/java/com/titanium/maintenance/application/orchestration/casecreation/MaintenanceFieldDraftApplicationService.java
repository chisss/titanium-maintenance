package com.titanium.maintenance.application.orchestration.casecreation;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.orchestration.configuration.MaintenanceFrozenConfigurationReader;
import com.titanium.maintenance.command.ProposeMaintenanceFieldChangesCommand;
import com.titanium.maintenance.common.enums.PolicyMaintenanceSnapshotFailureReason;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.common.exception.PolicyMaintenanceSnapshotException;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogRequest;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
import com.titanium.maintenance.port.ruleengine.MaintenanceConditionRulePort;
import com.titanium.maintenance.port.ruleengine.MaintenanceConditionRulePort.ConditionRuleRequest;
import com.titanium.maintenance.query.repository.MaintenanceCaseItemViewRepository;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceCaseItemView;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldCatalogSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldDescriptorSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldProposal;

import lombok.RequiredArgsConstructor;

/** 独立案件字段草稿编排；权威取数后交由聚合完成最终业务校验。 */
@Service
@RequiredArgsConstructor
public class MaintenanceFieldDraftApplicationService {

    private final PolicyMaintenanceSnapshotPort policyMaintenanceSnapshotPort;
    private final PolicyFieldCatalogPort policyFieldCatalogPort;
    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final MaintenanceCaseItemViewRepository caseItemViewRepository;
    private final MaintenanceFrozenConfigurationReader frozenConfigurationReader;
    private final MaintenanceConditionRulePort conditionRulePort;

    /** 获取 Policy 当前快照和目录证据，再发送单个权威字段提案命令。 */
    public CompletableFuture<Void> record(MaintenanceFieldDraftRequest request) {
        MaintenanceView caseView = requireCaseView(request);
        String policyId = requirePolicyId(caseView);
        PolicyMaintenanceSnapshot currentSnapshot = policyMaintenanceSnapshotPort.capture(
                new PolicyMaintenanceSnapshotRequest(policyId, request.tenantId()));
        validateCurrentSnapshot(request.tenantId(), policyId, currentSnapshot);
        List<MaintenanceFieldProposal> proposals = request.proposals().stream()
                .map(input -> new MaintenanceFieldProposal(
                        input.objectId(), input.fieldCode(), input.dataType(), input.canonicalValue()))
                .toList();
        PolicyFieldCatalogEvidence catalog = policyFieldCatalogPort.getCatalog(
                new PolicyFieldCatalogRequest(
                        request.tenantId(), null, null,
                        currentSnapshot.businessEffectiveAt().toLocalDate()));
        requireExecutableFields(proposals, catalog);
        requireConditionRulesAccepted(caseView, request, proposals);
        MaintenanceFieldCatalogSnapshot catalogSnapshot = catalogSnapshot(
                request, currentSnapshot, proposals, catalog);
        ProposeMaintenanceFieldChangesCommand command = new ProposeMaintenanceFieldChangesCommand(
                MaintenanceId.of(request.maintenanceId()), request.itemCode(), currentSnapshot,
                proposals, catalogSnapshot, request.operatorId(), request.tenantId());
        return commandGateway.send(command).thenApply(ignored -> null);
    }

    /**
     * 受理环节预检字段可执行性：目录中 {@code executionSupported=false} 的字段在本域没有落地执行器。
     * <p>
     * 这类字段此前「受理放行、生效必败」——{@code MaintenanceEffectApplicationService} 的
     * {@code validateExecutionCapabilities} 是唯一拦截点，用户须走完审核流程才拿到必然的失败。
     * 此处提前到受理环节拒绝，失败点与失败原因同时前移。
     * </p>
     * <p>
     * 🔴 判据取<b>实时目录证据</b>（{@link PolicyFieldCatalogEvidence}），而非本次冻结的
     * {@code MaintenanceFieldCatalogSnapshot}：后者不携带 {@code executionSupported}（缺口登记 G3 未解），
     * 且为其补字段会改变 {@code sameAuthorityAs} 所依赖的 {@code fields.equals()} 语义，使历史事件
     * 反序列化出的旧快照与新采快照永不相等。取值口径因此与生效环节保持同源（同为实时目录），不引入第二套权威。
     * </p>
     * <p>
     * 与生效环节的差异仅在<b>时机</b>：本预检对「同一字段多次提案」按字段码去重，失败信息与错误码两处一致。
     * </p>
     */
    private void requireExecutableFields(
            List<MaintenanceFieldProposal> proposals,
            PolicyFieldCatalogEvidence catalog) {
        proposals.forEach(proposal -> {
            if (!catalog.requireField(proposal.fieldCode()).capability().executionSupported()) {
                throw validation("fieldCode", "字段尚未开放真实执行: " + proposal.fieldCode());
            }
        });
    }

    /**
     * 受理环节执行字段条件规则（AC-04）：冻结配置中字段声明 {@code conditionRuleCode} 时，
     * 把该规则送规则引擎求值；条件不成立 ⇒ 该字段<b>拒收</b>，且发生在派发命令之前。
     * <p>
     * 🔴 <b>求值依据取案件冻结配置</b>（{@code MaintenanceItemConfiguration.getDefinition().fieldRules()}），
     * 与生效环节读到的配置是同一份——读取经 {@link MaintenanceFrozenConfigurationReader} 做三元核对，
     * 不引入第二套权威。未被冻结配置声明字段规则的字段不受约束（可编辑性判定仍在聚合侧
     * {@code MaintenanceFieldProposalPlanner.requireEditableRule}，本预检只<b>新增</b>条件规则的执行，
     * 不放宽也不收紧可改范围）。
     * </p>
     * <p>
     * 🔴 <b>失败语义不降级</b>：规则引擎不可达 / 规则集无效时适配器直接抛出
     * （{@code MAINTENANCE_RULE_ENGINE_REMOTE_ERROR}），此处不做任何 catch——
     * 把「问不到规则」伪装成「条件不成立」会让服务故障以业务拒绝的形态呈现给用户。
     * </p>
     */
    private void requireConditionRulesAccepted(
            MaintenanceView caseView,
            MaintenanceFieldDraftRequest request,
            List<MaintenanceFieldProposal> proposals) {
        Map<String, MaintenanceFieldRule> fieldRules = frozenFieldRules(caseView, request);
        proposals.forEach(proposal -> {
            MaintenanceFieldRule rule = fieldRules.get(proposal.fieldCode());
            if (rule == null || rule.conditionRuleCode() == null) {
                return;
            }
            boolean accepted = conditionRulePort.isAccepted(new ConditionRuleRequest(
                    request.tenantId(), request.maintenanceId(), rule.conditionRuleCode(),
                    request.itemCode(), proposal.fieldCode(), proposal.canonicalValue()));
            if (!accepted) {
                throw validation("fieldCode", "字段不满足条件规则: " + proposal.fieldCode());
            }
        });
    }

    /** 读取案件冻结配置的字段规则（按字段码索引）；配置读取口径唯一，见 {@link MaintenanceFrozenConfigurationReader}。 */
    private Map<String, MaintenanceFieldRule> frozenFieldRules(
            MaintenanceView caseView, MaintenanceFieldDraftRequest request) {
        MaintenanceCaseItemView itemView = caseItemViewRepository
                .findByTenantIdAndMaintenanceIdAndItemCode(
                        request.tenantId(), request.maintenanceId(), request.itemCode())
                .orElseThrow(MaintenanceNotFoundException::new);
        return frozenConfigurationReader.requireFrozenConfiguration(caseView, itemView)
                .getDefinition().fieldRules().stream()
                .collect(Collectors.toUnmodifiableMap(MaintenanceFieldRule::fieldCode, rule -> rule));
    }

    private MaintenanceView requireCaseView(MaintenanceFieldDraftRequest request) {
        return maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        request.maintenanceId(), request.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
    }

    private String requirePolicyId(MaintenanceView caseView) {
        if (caseView.getPolicyId() == null || caseView.getPolicyId().isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldDraftApplicationService", "caseContext", "案件查询上下文缺少保单标识");
        }
        return caseView.getPolicyId();
    }

    private void validateCurrentSnapshot(
            String tenantId,
            String policyId,
            PolicyMaintenanceSnapshot snapshot) {
        if (snapshot == null) {
            throw snapshotFailure(
                    PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE,
                    "Policy 当前快照服务未返回权威证据");
        }
        if (!tenantId.equals(snapshot.tenantId())) {
            throw snapshotFailure(
                    PolicyMaintenanceSnapshotFailureReason.TENANT_MISMATCH,
                    "Policy 当前快照租户回显不一致");
        }
        if (!policyId.equals(snapshot.policyId().id())) {
            throw snapshotFailure(
                    PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID,
                    "Policy 当前快照保单标识回显不一致");
        }
    }

    /**
     * 冻结本次草稿所依赖的字段描述快照。
     * <p>
     * 🔴 取值口径 = <b>本保全项目录声明字段 ∪ 本次提案实际引用的字段</b>，二者取并集：
     * </p>
     * <ul>
     *   <li><b>项目录声明字段</b>（{@code itemCode == capability().changeTypeCode}）：字段型保全项的既定口径，
     *       保持既有行为不变；</li>
     *   <li><b>提案引用字段</b>：金额型保全项（{@code ADDITIONAL_PAYMENT}/{@code REDUCTION_PAYMENT}/
     *       {@code TOP_UP}/{@code PARTIAL_WITHDRAWAL}）在 Policy 字段目录中<b>没有任何</b>以自身编码声明
     *       {@code changeTypeCode} 的字段，仅按上一口径过滤会得到空集，草稿在受理环节即被拒
     *       （{@code MaintenanceFieldCatalogSnapshot} 的「字段目录快照不能为空」不变量），
     *       使这类保全项的整条链路（草稿 → 报价 → 结算 → 生效）结构性不可达。</li>
     * </ul>
     * <p>
     * 🔴 <b>可编辑性的权威不在本快照</b>：字段是否属于该保全项由<b>案件冻结配置</b>的
     * {@code MaintenanceItemInstance.fieldRules()} 判定（{@code MaintenanceFieldProposalPlanner}
     * 的 {@code requireEditableRule}），本快照只提供描述信息与归属记录。并集因此只放宽「目录覆盖范围」，
     * 不放宽「哪些字段可改」——提案仍须同时在冻结配置的可编辑白名单内才可能通过。
     * </p>
     * <p>
     * 🔴 取并集而非直接改用提案字段：既有字段型草稿的快照内容保持不变，{@code sameAuthorityAs}
     * 所依赖的 {@code fields.equals()} 语义对存量案件不发生漂移（幂等早返回不被破坏）。
     * </p>
     */
    private MaintenanceFieldCatalogSnapshot catalogSnapshot(
            MaintenanceFieldDraftRequest request,
            PolicyMaintenanceSnapshot currentSnapshot,
            List<MaintenanceFieldProposal> proposals,
            PolicyFieldCatalogEvidence catalog) {
        if (catalog == null
                || !request.tenantId().equals(catalog.tenantId())
                || !currentSnapshot.businessEffectiveAt().toLocalDate().equals(catalog.businessDate())) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldDraftApplicationService", "fieldCatalog", "Policy 字段目录回显不一致");
        }
        Map<String, MaintenanceFieldDescriptorSnapshot> fields = new TreeMap<>();
        Set<String> proposalFieldCodes = proposals.stream()
                .map(MaintenanceFieldProposal::fieldCode)
                .collect(Collectors.toCollection(HashSet::new));
        proposals.forEach(proposal -> catalog.requireField(proposal.fieldCode()));
        catalog.fields().stream()
                .filter(descriptor -> request.itemCode().equals(descriptor.capability().changeTypeCode())
                        || proposalFieldCodes.contains(descriptor.fieldCode()))
                .forEach(descriptor -> fields.put(descriptor.fieldCode(), descriptorSnapshot(descriptor)));
        return new MaintenanceFieldCatalogSnapshot(
                catalog.tenantId(), catalog.businessDate(), catalog.catalogVersion(), catalog.contentHash(),
                OffsetDateTime.now(ZoneOffset.UTC), fields);
    }

    private MaintenanceFieldDescriptorSnapshot descriptorSnapshot(
            PolicyFieldDescriptorEvidence descriptor) {
        return new MaintenanceFieldDescriptorSnapshot(
                descriptor.fieldCode(), descriptor.objectType(), descriptor.valueType(), descriptor.labelKey(),
                descriptor.collection(), descriptor.objectIdentityField(), descriptor.capability().readable(),
                descriptor.capability().proposable(), descriptor.capability().clearable(),
                descriptor.capability().requiresObjectId(), descriptor.capability().changeTypeCode(),
                descriptor.sensitivity(), descriptor.maskingPolicy(), descriptor.deprecatedAt());
    }

    private PolicyMaintenanceSnapshotException snapshotFailure(
            PolicyMaintenanceSnapshotFailureReason reason,
            String message) {
        return new PolicyMaintenanceSnapshotException(reason, message);
    }

    private MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException(
                "MaintenanceFieldDraftApplicationService", fieldName, message);
    }
}
