package com.titanium.maintenance.application.orchestration.casecreation;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
import com.titanium.maintenance.valueobject.admission.MaintenanceAdmissionMatrix;
import com.titanium.maintenance.valueobject.admission.MaintenanceServiceRestrictionAdmission;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.item.MaintenanceItemCode;
import com.titanium.maintenance.valueobject.item.MaintenanceItemSelectionEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectSchedule;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

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
    private final MaintenanceInFlightAdmissionPolicy inFlightAdmissionPolicy;

    /** 先解析全部权威证据，再按创建、逐项冻结、完成门禁的顺序发送命令。 */
    public CompletableFuture<String> create(MaintenanceCaseCreationRequest request) {
        PolicyMaintenanceSnapshot snapshot = policyMaintenanceSnapshotPort.capture(
                new PolicyMaintenanceSnapshotRequest(request.policyId(), request.tenantId()));
        validateSnapshot(request, snapshot);
        List<ResolvedMaintenanceItem> resolvedItems = resolveOfferingAndConfigurations(request, snapshot);
        validateEffectiveSelection(request, snapshot, resolvedItems);
        // g04-07：领域中立的状态准入（保全项 × 保单状态），独立路径原先**只有** Offering 的服务端状态过滤，
        // 无领域兜底——同一 Offering 数据可声明出与保单域状态机不相容的组合（真机实测：
        // allowedPolicyStatuses 含 PENDING_EFFECTIVE × SURRENDER，而 NOT_EFFECTIVE 状态机只可 → EFFECTIVE/CANCELLED，
        // 不可 → TERMINATED）。矩阵是**必要**条件，Offering 只在其上额外收窄，不得放宽。
        requireAdmittedStatuses(request, snapshot);
        // g04-09：服务受限准入（受限 × 保全项），与上一行正交——挂失保单仍是 EFFECTIVE，状态准入看不出来
        requireNotServiceRestricted(request, snapshot);
        // g04-07：跨案在途/互斥（与旧版入口共用同一门面）
        inFlightAdmissionPolicy.requireNoConflict(
                request.policyId(), request.tenantId(), request.clientRequestKey(), request.source(),
                legacyMaintenanceTypes(request));
        CreateMaintenanceCaseCommand command = CreateMaintenanceCaseCommand.of(
                request.policyId(), request.itemCodes(), request.effectiveTimeType(),
                request.specificEffectiveDate(), request.description(), snapshot, request.clientRequestKey(),
                request.source(), request.createdBy(), request.tenantId());
        // 🔴 未来生效计划必须在**建案命令之前**解析完毕。建案与排期是两条独立命令、各自独立事务：
        //    解析若留在命令链尾，失败就发生在案件已落库之后——HTTP 报失败，库里却留下一张无排期、
        //    永不执行、永不终态的 PENDING 案件；而跨案在途门面按**保单**拦截，该保单自此**永久无法建案**
        //    （真机实测：请求返 71000005「Policy 未提供可用的下一保单年度起点」，案件 d4e7544a 仍落库，
        //    其后同一保单建案即被 71001003「存在在途保全案件」拒绝）。
        //    与快照 / Offering / 状态准入 / 在途冲突同形——全部前置，失败即拒、零副作用。
        ScheduleMaintenanceEffectCommand scheduleCommand = resolveScheduleCommand(request, snapshot, command);
        CompletableFuture<Object> flow = sendCreateOrRecover(command);
        for (ResolvedMaintenanceItem item : resolvedItems) {
            flow = flow.thenCompose(ignored -> commandGateway.send(new AddMaintenanceItemCommand(
                    command.id(), item.definition(), item.evidence(), request.createdBy(), request.tenantId())));
        }
        return flow.thenCompose(ignored -> commandGateway.send(
                        new CompleteMaintenanceCaseInitializationCommand(
                                command.id(), request.itemCodes(), request.createdBy(), request.tenantId())))
                .thenCompose(ignored -> scheduleCommand == null
                        ? CompletableFuture.completedFuture(ignored)
                        : commandGateway.send(scheduleCommand))
                .thenApply(ignored -> command.id().id());
    }

    /**
     * 解析未来生效计划（<b>只解析，不发送</b>——发送留在建案命令链上）。
     *
     * <p>
     * 返回 {@code null} 表示该生效方式属「建案即执行」型、无需未来计划。这是一条<b>显式分类结果</b>，
     * 不是兜底默认：{@link MaintenanceEffectSchedule#supportsScheduling} 是穷尽 switch（无 {@code default}），
     * 取 {@code false} 的只可能是 IMMEDIATE / RETROACTIVE。新增生效方式若忘了表态，编译期即失败，
     * 不会静默落进本分支——本分支的静默跳过正是 NEXT_PERIOD 改造前的缺口形态（枚举与字典均已登记，
     * 但案件建出来后永远不会被排期执行，且无人报错）。
     * </p>
     */
    private ScheduleMaintenanceEffectCommand resolveScheduleCommand(
            MaintenanceCaseCreationRequest request,
            PolicyMaintenanceSnapshot snapshot,
            CreateMaintenanceCaseCommand command) {
        if (!MaintenanceEffectSchedule.supportsScheduling(request.effectiveTimeType())) {
            return null;
        }
        String zoneId = tenantTimeZonePort.resolveZoneId(request.tenantId());
        LocalDateTime tenantExecutionAt = resolveNextExecutionAt(request, snapshot, zoneId);
        if (!tenantExecutionAt.isAfter(LocalDateTime.now(ZoneId.of(zoneId)))) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectSchedule", "nextExecutionAt", "未来生效计划时间必须晚于当前租户时间");
        }
        LocalDateTime nextExecutionAt = tenantExecutionAt.atZone(ZoneId.of(zoneId))
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return new ScheduleMaintenanceEffectCommand(
                command.id(), scheduleId(command.id().id()), zoneId, nextExecutionAt,
                request.createdBy(), request.tenantId());
    }

    private LocalDateTime resolveNextExecutionAt(
            MaintenanceCaseCreationRequest request,
            PolicyMaintenanceSnapshot snapshot,
            String zoneId) {
        // 穷尽 switch（无 default）：新增生效方式必须在此表态其执行时点来源，否则编译期失败。
        return switch (request.effectiveTimeType()) {
            case FUTURE, SPECIFIED_DATE -> request.specificEffectiveDate();
            case NEXT_BILLING_DATE -> toTenantTime(
                    snapshot.nextBillingDateAt(), zoneId, "Policy 未提供可用的下一缴费日");
            // 🔴 NEXT_PERIOD（次期生效）与 POLICY_ANNIVERSARY（保单周年日）**同义**，必须同分支、同快照字段
            //    （解析结果逐字相同由同义用例锁定）；不得为 NEXT_PERIOD 另取日期来源。
            case NEXT_PERIOD, POLICY_ANNIVERSARY -> toTenantTime(
                    snapshot.nextPolicyAnniversaryAt(), zoneId, "Policy 未提供可用的下一保单年度起点");
            // 建案即执行型：由 supportsScheduling 闸口在上游拦下，此处显式列出以满足穷尽性。
            case IMMEDIATE, RETROACTIVE -> throw new MaintenanceValidationException(
                    "MaintenanceEffectSchedule", "effectiveTimeType", "当前生效类型不能创建未来计划");
        };
    }

    private LocalDateTime toTenantTime(
            OffsetDateTime value,
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

    /**
     * g04-07 领域中立状态准入：逐保全项断言「(保全项类型, 保单状态)」在准入矩阵取值内。
     *
     * <p>
     * 本方法补的是独立路径原先<b>完全缺失</b>的领域兜底——该路径此前只依赖 Product Offering 的服务端
     * {@code allowedPolicyStatuses} 过滤，而 Offering 是可配数据，能声明出与保单域状态机不相容的组合。
     * </p>
     */
    private void requireAdmittedStatuses(MaintenanceCaseCreationRequest request, PolicyMaintenanceSnapshot snapshot) {
        for (MaintenanceType type : legacyMaintenanceTypes(request)) {
            MaintenanceAdmissionMatrix.require(type, snapshot.policyStatus());
        }
    }

    /**
     * g04-09 服务受限准入：保单处于挂失等服务受限之下时，减损类保全项（退保、保单贷款）在建案即拒绝。
     *
     * <p>
     * 这是准入的<b>第二维度</b>（受限 × 保全项），与上一方法的状态维度正交——挂失保单仍是
     * {@code EFFECTIVE}，状态准入看不出任何异常。判据取自建案快照的受限字段（来自保单读模型，最终一致），
     * 故只是<b>前置收窄</b>：明显受限的案件不必走完审批才在生效环节被拒。权威拒绝在保单域写侧
     * （{@code Policy.requireNotServiceRestricted} 与 {@code PolicyLoanApplicationService.disburse}），
     * 快照滞后漏拒时由其兜住。
     * </p>
     *
     * <p>
     * 🔴 <b>覆盖边界</b>：本闸口走的是独立建案路径（唯一携带 {@code PolicyMaintenanceSnapshot} 的路径）。
     * 旧版整案建案入口（开关控制、面向历史在途案件）的状态契约
     * （{@code PolicyServicePort.PolicyStatusSnapshot}）<b>不携带受限维度</b>，其受理侧不做本项前置，
     * 由保单域生效期权威拒绝兜底。该通道为存量兼容通道，不为此单独扩契约。
     * </p>
     */
    private void requireNotServiceRestricted(MaintenanceCaseCreationRequest request,
            PolicyMaintenanceSnapshot snapshot) {
        MaintenanceServiceRestrictionAdmission.require(
                snapshot.serviceRestricted(), legacyMaintenanceTypes(request));
    }

    /**
     * 保全项编码 → 旧主类型。
     *
     * <p>
     * 与生效环节 {@code MaintenanceEffectApplicationService} 走<b>同一桥接</b>
     * （{@link MaintenanceItemCode#legacyMaintenanceType()}），故准入判定与执行判定看到的是同一类型；
     * 无映射的编码由该桥接<b>显式拒绝</b>（契约见 {@code MaintenanceEffectPayloadKind} 类注释），
     * 与准入矩阵「未登记即拒绝」同向。
     * </p>
     */
    private List<MaintenanceType> legacyMaintenanceTypes(MaintenanceCaseCreationRequest request) {
        return request.itemCodes().stream()
                .map(code -> MaintenanceItemCode.of(code).legacyMaintenanceType())
                .toList();
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
