package com.titanium.maintenance.application.orchestration.casecreation;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.command.ProposeMaintenanceFieldChangesCommand;
import com.titanium.maintenance.common.enums.PolicyMaintenanceSnapshotFailureReason;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.common.exception.PolicyMaintenanceSnapshotException;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogRequest;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
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

    /** 获取 Policy 当前快照和目录证据，再发送单个权威字段提案命令。 */
    public CompletableFuture<Void> record(MaintenanceFieldDraftRequest request) {
        String policyId = resolvePolicyId(request);
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

    private String resolvePolicyId(MaintenanceFieldDraftRequest request) {
        MaintenanceView caseView = maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        request.maintenanceId(), request.tenantId())
                .orElseThrow(MaintenanceNotFoundException::new);
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
        proposals.forEach(proposal -> catalog.requireField(proposal.fieldCode()));
        catalog.fields().stream()
                .filter(descriptor -> request.itemCode().equals(descriptor.capability().changeTypeCode()))
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
