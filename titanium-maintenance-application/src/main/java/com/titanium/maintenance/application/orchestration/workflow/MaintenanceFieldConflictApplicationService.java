package com.titanium.maintenance.application.orchestration.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.RefreshMaintenanceFieldConflictsInput;
import com.titanium.maintenance.application.command.ResolveMaintenanceFieldConflictInput;
import com.titanium.maintenance.application.model.MaintenanceFieldConflictOperationResult;
import com.titanium.maintenance.command.RefreshMaintenanceFieldConflictsCommand;
import com.titanium.maintenance.command.ResolveMaintenanceFieldConflictCommand;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.PolicyMaintenanceApplicationPort;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldConflictPlan;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;

import lombok.RequiredArgsConstructor;

/** 应用层编排 Policy 最新快照读取与保全字段冲突命令。 */
@Service
@RequiredArgsConstructor
public class MaintenanceFieldConflictApplicationService {

    private final PolicyMaintenanceSnapshotPort policyMaintenanceSnapshotPort;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final CommandGateway commandGateway;

    public CompletableFuture<MaintenanceFieldConflictOperationResult> refresh(
            RefreshMaintenanceFieldConflictsInput input) {
        MaintenanceView view = requireCase(input.maintenanceId(), input.tenantId());
        PolicyMaintenanceSnapshot snapshot = capture(view, input.tenantId());
        return refresh(input, snapshot);
    }

    /** 生效编排专用：版本未漂移时不写事件，避免重复刷新案件。 */
    public CompletableFuture<MaintenanceFieldConflictOperationResult> refreshIfVersionChanged(
            RefreshMaintenanceFieldConflictsInput input,
            long expectedPolicyVersion) {
        MaintenanceView view = requireCase(input.maintenanceId(), input.tenantId());
        PolicyMaintenanceSnapshot snapshot = capture(view, input.tenantId());
        if (snapshot.policyVersion() == expectedPolicyVersion) {
            return CompletableFuture.completedFuture(null);
        }
        if (snapshot.policyVersion() < expectedPolicyVersion) {
            throw validation("policyVersion", "Policy 最新版本早于案件期望版本");
        }
        String refreshOperationId = PolicyMaintenanceApplicationPort.stageOperationId(
                input.operationId(), "field-conflict-refresh-policy-v" + snapshot.policyVersion(), 0);
        RefreshMaintenanceFieldConflictsInput versionedInput = new RefreshMaintenanceFieldConflictsInput(
                input.maintenanceId(), refreshOperationId,
                input.operatorId(), input.tenantId());
        return refresh(versionedInput, snapshot);
    }

    public CompletableFuture<MaintenanceFieldConflictOperationResult> resolve(
            ResolveMaintenanceFieldConflictInput input) {
        requireCase(input.maintenanceId(), input.tenantId());
        MaintenanceFieldValue reenteredValue = input.action() == MaintenanceFieldConflictResolutionAction.REENTER
                ? new MaintenanceFieldValue(input.dataType(), input.canonicalValue())
                : null;
        String requestHash = hash(
                input.tenantId(), input.maintenanceId(), input.operationId(), input.itemCode(),
                input.objectId(), input.fieldCode(), input.action().getCode(),
                input.dataType() == null ? null : input.dataType().getCode(), input.canonicalValue(), input.reason());
        OffsetDateTime resolvedAt = OffsetDateTime.now(ZoneOffset.UTC);
        ResolveMaintenanceFieldConflictCommand command = new ResolveMaintenanceFieldConflictCommand(
                MaintenanceId.of(input.maintenanceId()), input.operationId(), requestHash, input.itemCode(),
                input.objectId(), input.fieldCode(), input.action(), reenteredValue, input.reason(), resolvedAt,
                input.operatorId(), input.tenantId());
        return commandGateway.<MaintenanceFieldConflictPlan>send(command)
                .thenApply(plan -> result(input.operationId(), plan));
    }

    private CompletableFuture<MaintenanceFieldConflictOperationResult> refresh(
            RefreshMaintenanceFieldConflictsInput input,
            PolicyMaintenanceSnapshot snapshot) {
        String requestHash = hash(
                input.tenantId(), input.maintenanceId(), input.operationId(), snapshot.policyId().id(),
                Long.toString(snapshot.policyVersion()), snapshot.beforeSnapshot().contentHash());
        OffsetDateTime refreshedAt = OffsetDateTime.now(ZoneOffset.UTC);
        RefreshMaintenanceFieldConflictsCommand command = new RefreshMaintenanceFieldConflictsCommand(
                MaintenanceId.of(input.maintenanceId()), input.operationId(), requestHash, snapshot,
                refreshedAt, input.operatorId(), input.tenantId());
        return commandGateway.<MaintenanceFieldConflictPlan>send(command)
                .thenApply(plan -> result(input.operationId(), plan));
    }

    private MaintenanceView requireCase(String maintenanceId, String tenantId) {
        return maintenanceViewRepository
                .findByMaintenanceIdAndTenantIdAndIndependentCaseTrueAndInitializationCompletedTrue(
                        maintenanceId, tenantId)
                .orElseThrow(MaintenanceNotFoundException::new);
    }

    private PolicyMaintenanceSnapshot capture(MaintenanceView view, String tenantId) {
        if (view.getPolicyId() == null || view.getPolicyId().isBlank()) {
            throw validation("policyId", "案件查询上下文缺少保单标识");
        }
        PolicyMaintenanceSnapshot snapshot = policyMaintenanceSnapshotPort.capture(
                new PolicyMaintenanceSnapshotRequest(view.getPolicyId(), tenantId));
        if (snapshot == null || !tenantId.equals(snapshot.tenantId())
                || !view.getPolicyId().equals(snapshot.policyId().id())) {
            throw validation("currentPolicySnapshot", "Policy 最新快照回显与案件不一致");
        }
        return snapshot;
    }

    private MaintenanceFieldConflictOperationResult result(
            String operationId,
            MaintenanceFieldConflictPlan plan) {
        return new MaintenanceFieldConflictOperationResult(
                operationId, plan.proposedSnapshot().policyVersion(), plan.proposedSnapshot().contentHash(),
                plan.conflictCount(), plan.allChanges());
    }

    private String hash(String... values) {
        StringBuilder canonical = new StringBuilder();
        for (String value : values) {
            canonical.append(value == null ? -1 : value.length()).append(':');
            if (value != null) {
                canonical.append(value);
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }

    private MaintenanceValidationException validation(String field, String message) {
        return new MaintenanceValidationException(
                "MaintenanceFieldConflictApplicationService", field, message);
    }
}
