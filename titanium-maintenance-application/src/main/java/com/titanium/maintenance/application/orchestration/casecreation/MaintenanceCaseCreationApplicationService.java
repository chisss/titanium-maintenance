package com.titanium.maintenance.application.orchestration.casecreation;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.springframework.stereotype.Service;

import com.titanium.maintenance.command.AddMaintenanceItemCommand;
import com.titanium.maintenance.command.CompleteMaintenanceCaseInitializationCommand;
import com.titanium.maintenance.command.CreateMaintenanceCaseCommand;
import com.titanium.maintenance.command.ScheduleMaintenanceEffectCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.PolicyMaintenanceSnapshotFailureReason;
import com.titanium.maintenance.common.enums.ProductMaintenanceOfferingFailureReason;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceConflictException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.common.exception.PolicyMaintenanceSnapshotException;
import com.titanium.maintenance.common.exception.ProductMaintenanceOfferingException;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.port.policy.PolicyMaintenanceSnapshotPort.PolicyMaintenanceSnapshotRequest;
import com.titanium.maintenance.port.product.ProductMaintenanceOfferingPort;
import com.titanium.maintenance.port.product.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingEvidence;
import com.titanium.maintenance.port.product.ProductMaintenanceOfferingPort.ProductMaintenanceOfferingRequest;
import com.titanium.maintenance.port.tenant.TenantTimeZonePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.item.MaintenanceItemSelectionEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectSchedule;

import lombok.RequiredArgsConstructor;

/** 独立保全建案应用编排入口；解析全部权威证据后完成创建、逐项冻结和初始化门禁。 */
@Service
@RequiredArgsConstructor
public class MaintenanceCaseCreationApplicationService {

    private final PolicyMaintenanceSnapshotPort policyMaintenanceSnapshotPort;
    private final ProductMaintenanceOfferingPort productMaintenanceOfferingPort;
    private final MaintenanceItemConfigurationRepository configurationRepository;
    private final CommandGateway commandGateway;
    private final MaintenanceViewRepository maintenanceViewRepository;
    private final TenantTimeZonePort tenantTimeZonePort;

    /** 先解析全部权威证据，再按创建、逐项冻结、完成门禁的顺序发送命令。 */
    public CompletableFuture<String> create(MaintenanceCaseCreationRequest request) {
        PolicyMaintenanceSnapshot snapshot = policyMaintenanceSnapshotPort.capture(
                new PolicyMaintenanceSnapshotRequest(request.policyId(), request.tenantId()));
        validateSnapshot(request, snapshot);
        List<ResolvedMaintenanceItem> resolvedItems = resolveOfferingAndConfigurations(request, snapshot);
        validateEffectiveSelection(request, snapshot, resolvedItems);
        CreateMaintenanceCaseCommand command = CreateMaintenanceCaseCommand.of(
                request.policyId(), request.itemCodes(), request.effectiveTimeType(),
                request.specificEffectiveDate(), request.description(), snapshot, request.clientRequestKey(),
                request.source(), request.createdBy(), request.tenantId());
        CompletableFuture<Object> flow = sendCreateOrRecover(command);
        for (ResolvedMaintenanceItem item : resolvedItems) {
            flow = flow.thenCompose(ignored -> commandGateway.send(new AddMaintenanceItemCommand(
                    command.id(), item.definition(), item.evidence(), request.createdBy())));
        }
        return flow.thenCompose(ignored -> commandGateway.send(
                        new CompleteMaintenanceCaseInitializationCommand(
                                command.id(), request.itemCodes(), request.createdBy())))
                .thenCompose(ignored -> scheduleIfRequired(request, snapshot, command))
                .thenApply(ignored -> command.id().id());
    }

    private CompletableFuture<Object> scheduleIfRequired(
            MaintenanceCaseCreationRequest request,
            PolicyMaintenanceSnapshot snapshot,
            CreateMaintenanceCaseCommand command) {
        if (!MaintenanceEffectSchedule.supportsScheduling(request.effectiveTimeType())) {
            return CompletableFuture.completedFuture(command.id().id());
        }
        String zoneId = tenantTimeZonePort.resolveZoneId(request.tenantId());
        LocalDateTime tenantExecutionAt = resolveNextExecutionAt(request, snapshot, zoneId);
        if (!tenantExecutionAt.isAfter(LocalDateTime.now(ZoneId.of(zoneId)))) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectSchedule", "nextExecutionAt", "未来生效计划时间必须晚于当前租户时间");
        }
        LocalDateTime nextExecutionAt = tenantExecutionAt.atZone(ZoneId.of(zoneId))
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return commandGateway.send(new ScheduleMaintenanceEffectCommand(
                command.id(), scheduleId(command.id().id()), zoneId, nextExecutionAt, request.createdBy()));
    }

    private LocalDateTime resolveNextExecutionAt(
            MaintenanceCaseCreationRequest request,
            PolicyMaintenanceSnapshot snapshot,
            String zoneId) {
        return switch (request.effectiveTimeType()) {
            case FUTURE, SPECIFIED_DATE -> request.specificEffectiveDate();
            case NEXT_BILLING_DATE -> toTenantTime(
                    snapshot.nextBillingDateAt(), zoneId, "Policy 未提供可用的下一缴费日");
            case POLICY_ANNIVERSARY -> toTenantTime(
                    snapshot.nextPolicyAnniversaryAt(), zoneId, "Policy 未提供可用的下一保单周年日");
            default -> throw new MaintenanceValidationException(
                    "MaintenanceEffectSchedule", "effectiveTimeType", "当前生效类型不能创建未来计划");
        };
    }

    private LocalDateTime toTenantTime(
            java.time.OffsetDateTime value,
            String zoneId,
            String missingMessage) {
        if (value == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectSchedule", "nextExecutionAt", missingMessage);
        }
        return value.atZoneSameInstant(ZoneId.of(zoneId)).toLocalDateTime();
    }

    private String scheduleId(String maintenanceId) {
        return maintenanceId + ":effect";
    }

    private CompletableFuture<Object> sendCreateOrRecover(CreateMaintenanceCaseCommand command) {
        CompletableFuture<Object> createFuture = commandGateway.send(command);
        return createFuture.exceptionallyCompose(failure -> {
            if (!isConcurrentCreation(failure)) {
                return CompletableFuture.failedFuture(failure);
            }
            MaintenanceView winner = maintenanceViewRepository
                    .findByTenantIdAndSourceAndClientRequestKeyAndIndependentCaseTrue(
                            command.tenantId(), command.idempotencyKey().source(),
                            command.idempotencyKey().clientRequestKey())
                    .orElse(null);
            if (winner == null) {
                return CompletableFuture.failedFuture(failure);
            }
            if (!command.id().id().equals(winner.getMaintenanceId())
                    || !command.requestFingerprint().equals(winner.getRequestFingerprint())) {
                return CompletableFuture.failedFuture(new MaintenanceConflictException(
                        "CreateMaintenanceCaseCommand", "clientRequestKey", "幂等键已被不同建案请求占用"));
            }
            return CompletableFuture.completedFuture(winner.getMaintenanceId());
        });
    }

    private boolean isConcurrentCreation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConcurrencyException
                    || current instanceof AggregateStreamCreationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private List<ResolvedMaintenanceItem> resolveOfferingAndConfigurations(
            MaintenanceCaseCreationRequest request,
            PolicyMaintenanceSnapshot snapshot) {
        ProductMaintenanceOfferingEvidence offering = productMaintenanceOfferingPort.resolve(
                new ProductMaintenanceOfferingRequest(
                        request.tenantId(), snapshot.productId(), snapshot.productVersion(),
                        snapshot.planVersion(), snapshot.policyStatus(), request.source(),
                        snapshot.businessEffectiveAt()));
        validateOfferingEvidence(snapshot, offering);
        List<ResolvedMaintenanceItem> resolvedItems = new ArrayList<>();
        // Policy 返回租户业务本地时点及其偏移；配置有效期按同一租户的本地墙上时间存储。
        LocalDateTime businessTime = snapshot.businessEffectiveAt().toLocalDateTime();
        for (String itemCode : request.itemCodes()) {
            if (!offering.allowedItemCodes().contains(itemCode)) {
                throw offeringFailure(
                        ProductMaintenanceOfferingFailureReason.NOT_APPLICABLE,
                        "所选保全项不在Product Offering允许范围内: " + itemCode);
            }
            StoredConfiguration storedConfiguration = configurationRepository.findEffective(
                            request.tenantId(), itemCode, businessTime)
                    .orElseThrow(MaintenanceConfigurationNotFoundException::new);
            MaintenanceItemConfiguration configuration = storedConfiguration.configuration();
            MaintenanceItemDefinition definition = configuration.getDefinition();
            if (!configuration.isEffectiveAt(businessTime)
                    || !itemCode.equals(definition.itemCode())) {
                throw offeringFailure(
                        ProductMaintenanceOfferingFailureReason.NOT_APPLICABLE,
                        "已发布保全项配置与业务时点或项目编码不一致: " + itemCode);
            }
            if (!definition.supportsChannel(request.source())) {
                throw offeringFailure(
                        ProductMaintenanceOfferingFailureReason.NOT_APPLICABLE,
                        "已发布保全项配置未开放当前受理渠道: " + itemCode);
            }
            MaintenanceItemSelectionEvidence evidence = MaintenanceItemSelectionEvidence.authoritative(
                    configuration.getConfigurationId(), definition.version(), configuration.getContentHash(),
                    offering.offeringId(), offering.offeringVersion(), offering.contentHash(), offering.resolvedAt());
            resolvedItems.add(new ResolvedMaintenanceItem(definition, evidence));
        }
        validateCombination(resolvedItems);
        return List.copyOf(resolvedItems);
    }

    private void validateCombination(List<ResolvedMaintenanceItem> resolvedItems) {
        if (resolvedItems.size() > 1
                && resolvedItems.stream().anyMatch(item -> item.definition().atomicOnly())) {
            throw combinationFailure("整案原子保全项不能与其他项目组合");
        }
        for (int left = 0; left < resolvedItems.size(); left++) {
            for (int right = left + 1; right < resolvedItems.size(); right++) {
                MaintenanceItemDefinition first = resolvedItems.get(left).definition();
                MaintenanceItemDefinition second = resolvedItems.get(right).definition();
                if (!first.isCompatibleWith(second)) {
                    throw combinationFailure(
                            "保全项互斥，不能同案选择: " + first.itemCode() + " / " + second.itemCode());
                }
            }
        }
    }

    private void validateEffectiveSelection(
            MaintenanceCaseCreationRequest request,
            PolicyMaintenanceSnapshot snapshot,
            List<ResolvedMaintenanceItem> resolvedItems) {
        resolvedItems.forEach(item -> item.definition().effectiveRule().validateMode(request.effectiveTimeType()));
        if (!requiresDateValidation(request.effectiveTimeType())) {
            if (request.specificEffectiveDate() != null) {
                resolvedItems.forEach(item -> item.definition().effectiveRule().validateEffectiveDate(
                        request.effectiveTimeType(), request.specificEffectiveDate(), null, null));
            }
            return;
        }
        String zoneId = tenantTimeZonePort.resolveZoneId(request.tenantId());
        LocalDateTime referenceAt = LocalDateTime.now(ZoneId.of(zoneId));
        LocalDateTime policyEffectiveAt = snapshot.businessEffectiveAt()
                .atZoneSameInstant(ZoneId.of(zoneId)).toLocalDateTime();
        resolvedItems.forEach(item -> item.definition().effectiveRule().validateEffectiveDate(
                request.effectiveTimeType(), request.specificEffectiveDate(), policyEffectiveAt, referenceAt));
    }

    private boolean requiresDateValidation(EffectiveTimeType type) {
        return type == EffectiveTimeType.RETROACTIVE
                || type == EffectiveTimeType.FUTURE
                || type == EffectiveTimeType.SPECIFIED_DATE;
    }

    private void validateOfferingEvidence(
            PolicyMaintenanceSnapshot snapshot,
            ProductMaintenanceOfferingEvidence offering) {
        if (offering == null) {
            throw offeringFailure(
                    ProductMaintenanceOfferingFailureReason.UNAVAILABLE,
                    "Product保全Offering服务未返回权威证据");
        }
        if (!snapshot.tenantId().equals(offering.tenantId())
                || !snapshot.productId().equals(offering.productId())) {
            throw offeringFailure(
                    ProductMaintenanceOfferingFailureReason.CONTRACT_INVALID,
                    "Product保全Offering租户或产品回显不一致");
        }
        if (!snapshot.productVersion().equals(offering.productVersion())
                || !snapshot.planVersion().equals(offering.planVersion())) {
            throw offeringFailure(
                    ProductMaintenanceOfferingFailureReason.VERSION_MISMATCH,
                    "Product保全Offering版本与Policy快照不一致");
        }
    }

    private void validateSnapshot(
            MaintenanceCaseCreationRequest request,
            PolicyMaintenanceSnapshot snapshot) {
        if (snapshot == null) {
            throw failure(
                    PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE,
                    "Policy建案快照服务未返回权威证据");
        }
        if (!request.tenantId().equals(snapshot.tenantId())) {
            throw failure(
                    PolicyMaintenanceSnapshotFailureReason.TENANT_MISMATCH,
                    "Policy建案快照租户回显不一致");
        }
        if (!request.policyId().equals(snapshot.policyId().id())) {
            throw failure(
                    PolicyMaintenanceSnapshotFailureReason.CONTRACT_INVALID,
                    "Policy建案快照保单标识回显不一致");
        }
    }

    private PolicyMaintenanceSnapshotException failure(
            PolicyMaintenanceSnapshotFailureReason reason,
            String message) {
        return new PolicyMaintenanceSnapshotException(reason, message);
    }

    private ProductMaintenanceOfferingException offeringFailure(
            ProductMaintenanceOfferingFailureReason reason,
            String message) {
        return new ProductMaintenanceOfferingException(reason, message);
    }

    private MaintenanceValidationException combinationFailure(String message) {
        return new MaintenanceValidationException(
                "MaintenanceCaseCreationRequest", "itemCodes", message);
    }

    private record ResolvedMaintenanceItem(
            MaintenanceItemDefinition definition,
            MaintenanceItemSelectionEvidence evidence) {
    }
}
