package com.titanium.maintenance.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.number.IdGenerator;
import com.titanium.maintenance.application.model.casecreation.MaintenanceReadModel;
import com.titanium.maintenance.application.model.casecreation.MaintenanceSearchPageResult;
import com.titanium.maintenance.application.model.casecreation.MaintenanceStatisticsResult;
import com.titanium.maintenance.application.orchestration.casecreation.MaintenanceInFlightAdmissionPolicy;
import com.titanium.maintenance.command.AddMaintenanceChangeCommand;
import com.titanium.maintenance.command.CalculateMaintenancePremiumCommand;
import com.titanium.maintenance.command.ChangeMaintenanceStatusCommand;
import com.titanium.maintenance.command.CreateMaintenanceCommand;
import com.titanium.maintenance.command.ExecuteMaintenanceCommand;
import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceChangeType;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.exception.CustomerNotFoundException;
import com.titanium.maintenance.common.exception.InvalidMaintenanceStatusException;
import com.titanium.maintenance.common.exception.MaintenanceLegacyCreationDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceLegacyExecutionDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceLegacyExecutionIndependentCaseForbiddenException;
import com.titanium.maintenance.common.exception.MaintenanceLegacyPremiumCalculationDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.PolicyNotFoundException;
import com.titanium.maintenance.port.customer.CustomerServicePort;
import com.titanium.maintenance.port.maintenance.MaintenanceLegacyCreationFeaturePort;
import com.titanium.maintenance.port.maintenance.MaintenanceLegacyExecutionFeaturePort;
import com.titanium.maintenance.port.maintenance.MaintenanceLegacyPremiumCalculationFeaturePort;
import com.titanium.maintenance.port.policy.PolicyServicePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.admission.MaintenanceAdmissionMatrix;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * 保全应用服务（读写用例入口门面）
 * <p>
 * 写入口：跨域校验（保单/客户存在性、保单状态、在途/互斥）后经 {@link CommandGateway} 发命令，业务规则内聚在
 * {@code Maintenance} 聚合根。写侧收敛为纯事件溯源后，存在性/在途校验统一走 CQRS 读模型
 * {@link MaintenanceViewRepository}（表 {@code t_maintenance_view}，最终一致），不再读写侧状态表。
 * 在途/互斥规则委托 {@link MaintenanceInFlightAdmissionPolicy}（与独立建案入口共用同一门面）。
 * </p>
 */
@Service
@Transactional
public class MaintenanceApplicationService {

    private final CommandGateway                 commandGateway;
    private final MaintenanceViewRepository       maintenanceViewRepository;
    private final MaintenanceInFlightAdmissionPolicy inFlightAdmissionPolicy;
    private final PolicyServicePort               policyServicePort;
    private final CustomerServicePort             customerServicePort;
    private final MaintenanceLegacyCreationFeaturePort legacyCreationFeaturePort;
    private final MaintenanceLegacyPremiumCalculationFeaturePort legacyPremiumCalculationFeaturePort;
    private final MaintenanceLegacyExecutionFeaturePort legacyExecutionFeaturePort;
    private final IdGenerator                     idGenerator;

    public MaintenanceApplicationService(CommandGateway commandGateway,
                                         MaintenanceViewRepository maintenanceViewRepository,
                                         MaintenanceInFlightAdmissionPolicy inFlightAdmissionPolicy,
                                         PolicyServicePort policyServicePort,
                                         CustomerServicePort customerServicePort,
                                         MaintenanceLegacyCreationFeaturePort legacyCreationFeaturePort,
                                         MaintenanceLegacyPremiumCalculationFeaturePort legacyPremiumCalculationFeaturePort,
                                         MaintenanceLegacyExecutionFeaturePort legacyExecutionFeaturePort,
                                         IdGenerator idGenerator) {
        this.commandGateway = commandGateway;
        this.maintenanceViewRepository = maintenanceViewRepository;
        this.inFlightAdmissionPolicy = inFlightAdmissionPolicy;
        this.policyServicePort = policyServicePort;
        this.customerServicePort = customerServicePort;
        this.legacyCreationFeaturePort = legacyCreationFeaturePort;
        this.legacyPremiumCalculationFeaturePort = legacyPremiumCalculationFeaturePort;
        this.legacyExecutionFeaturePort = legacyExecutionFeaturePort;
        this.idGenerator = idGenerator;
    }

    // 创建保全案件
    public CompletableFuture<String> createMaintenanceCase(String policyId, String customerId,
                                                           MaintenanceType maintenanceType,
                                                           EffectiveTimeType effectiveTimeType,
                                                           LocalDateTime specificEffectiveDate, String description,
                                                           String createdBy, String tenantId) {
        if (!legacyCreationFeaturePort.isEnabled()) {
            throw new MaintenanceLegacyCreationDisabledException();
        }
        // 验证保单存在
        validatePolicyExists(policyId, tenantId);
        // 验证客户存在
        validateCustomer(customerId, tenantId);
        // 验证保单状态是否符合保全类型要求
        validatePolicyStatusForMaintenance(policyId, maintenanceType);
        // 检查在途案件与保全项互斥（与独立建案入口共用同一门面，见 MaintenanceInFlightAdmissionPolicy）
        inFlightAdmissionPolicy.requireNoConflict(policyId, tenantId, null, null, List.of(maintenanceType));

        // 保全 ID 由应用边界经 IdGenerator 生成后传入（DSN-05 / ADR-0002）
        CreateMaintenanceCommand command = CreateMaintenanceCommand.of(idGenerator.nextIdString(), policyId,
                customerId, maintenanceType, effectiveTimeType, specificEffectiveDate, description, createdBy, tenantId);
        return commandGateway.send(command).thenApply(result -> command.id().id());
    }

    // 添加保全变更记录
    public CompletableFuture<String> addMaintenanceChange(String maintenanceId, String changeType, String fieldName,
                                                          String oldValue, String newValue, String createdBy,
                                                          String tenantId) {
        requireMaintenanceExists(maintenanceId, tenantId);
        AddMaintenanceChangeCommand command = new AddMaintenanceChangeCommand(MaintenanceId.of(maintenanceId),
                MaintenanceChangeType.fromCode(changeType), fieldName, oldValue, newValue, createdBy, tenantId);
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 计算保全保费
    public CompletableFuture<String> calculateMaintenancePremium(String maintenanceId, BigDecimal totalAmount,
                                                                 BigDecimal refundAmount, String calculationDetails,
                                                                 String updatedBy, String tenantId) {
        if (!legacyPremiumCalculationFeaturePort.isEnabled()) {
            throw new MaintenanceLegacyPremiumCalculationDisabledException();
        }
        requireMaintenanceExists(maintenanceId, tenantId);
        CalculateMaintenancePremiumCommand command = new CalculateMaintenancePremiumCommand(
                MaintenanceId.of(maintenanceId), totalAmount, refundAmount, calculationDetails, updatedBy, tenantId);
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 执行保全
    public CompletableFuture<String> executeMaintenance(String maintenanceId, LocalDateTime effectiveTime,
                                                        String executionDetails, String updatedBy, String tenantId) {
        if (!legacyExecutionFeaturePort.isEnabled()) {
            throw new MaintenanceLegacyExecutionDisabledException();
        }
        MaintenanceView view = requireMaintenanceExists(maintenanceId, tenantId);
        // 通道互斥（缺陷 D9）：独立建案只能走案件平台任务级生效链，旧入口放行会使同一保单被两条通道重复施加
        if (view.isIndependentCase()) {
            throw new MaintenanceLegacyExecutionIndependentCaseForbiddenException();
        }
        // 验证保全状态（读模型最终一致）
        if (view.getStatus() != MaintenanceStatus.APPROVED) {
            throw new InvalidMaintenanceStatusException();
        }
        ExecuteMaintenanceCommand command = new ExecuteMaintenanceCommand(MaintenanceId.of(maintenanceId),
                effectiveTime, executionDetails, updatedBy, tenantId);
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 变更保全记录状态
    public CompletableFuture<String> changeMaintenanceStatus(String maintenanceId, MaintenanceStatus newStatus,
                                                             String changeReason, String changedBy, String tenantId) {
        requireMaintenanceExists(maintenanceId, tenantId);
        ChangeMaintenanceStatusCommand command = ChangeMaintenanceStatusCommand.of(maintenanceId, newStatus,
                changeReason, changedBy, tenantId);
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 根据ID查询保全记录（读模型 → 应用层读模型）
    @Transactional(readOnly = true)
    public MaintenanceReadModel findMaintenanceById(String id, String tenantId) {
        return toReadModel(requireMaintenanceExists(id, tenantId));
    }

    // 根据保单ID查询保全记录（读模型 → 应用层读模型）
    @Transactional(readOnly = true)
    public List<MaintenanceReadModel> findMaintenancesByPolicyId(String policyId, String tenantId) {
        return maintenanceViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).stream()
                .filter(MaintenanceView::isOperatorVisible)
                .map(this::toReadModel)
                .toList();
    }

    /**
     * 多条件搜索保全案件（内存过滤 + 手工分页）
     * <p>
     * 路由优先级：policyId → customerId → status 单条件全量 → 租户全部工单。
     * 在候选集基础上再按 maintenanceType、status 做内存过滤后分页返回。
     * </p>
     *
     * @param policyId        保单ID（可选）
     * @param customerId      客户ID（可选）
     * @param maintenanceType 保全类型码值（可选）
     * @param status          保全状态码值（可选）
     * @param page            页码（从0起）
     * @param size            每页条数
     * @return 分页后的保全读模型列表
     */
    @Transactional(readOnly = true)
    public List<MaintenanceReadModel> searchMaintenances(String policyId, String customerId,
                                                          String maintenanceType, String status,
                                                          int page, int size, String tenantId) {
        return searchMaintenancePage(policyId, customerId, maintenanceType, status, page, size, tenantId).list();
    }

    /** 搜索保全并返回真实过滤总数，供后台分页使用。 */
    @Transactional(readOnly = true)
    public MaintenanceSearchPageResult searchMaintenancePage(String policyId, String customerId,
                                                               String maintenanceType, String status,
                                                               int page, int size, String tenantId) {
        List<MaintenanceView> candidates;
        if (policyId != null && !policyId.isBlank()) {
            candidates = maintenanceViewRepository.findByPolicyIdAndTenantId(policyId, tenantId);
        } else if (customerId != null && !customerId.isBlank()) {
            candidates = maintenanceViewRepository.findByCustomerIdAndTenantId(customerId, tenantId);
        } else if (status != null && !status.isBlank()) {
            MaintenanceStatus statusEnum = MaintenanceStatus.fromCode(status);
            candidates = statusEnum != null
                    ? maintenanceViewRepository.findByTenantIdAndStatus(tenantId, statusEnum)
                    : List.of();
        } else {
            candidates = maintenanceViewRepository.findByTenantIdOrderByCreateTimeDesc(tenantId);
        }

        List<MaintenanceReadModel> filtered = candidates.stream()
                .filter(MaintenanceView::isOperatorVisible)
                .filter(v -> maintenanceType == null || maintenanceType.isBlank()
                        || (v.getMaintenanceType() != null
                                && maintenanceType.equals(v.getMaintenanceType().getValue())))
                .filter(v -> status == null || status.isBlank()
                        || (v.getStatus() != null && status.equals(v.getStatus().getCode())))
                .map(this::toReadModel)
                .toList();
        List<MaintenanceReadModel> pageItems = filtered.stream()
                .skip((long) page * size)
                .limit(size)
                .toList();
        return MaintenanceSearchPageResult.of(pageItems, filtered.size(), page + 1, size);
    }

    /**
     * 查询待处理保全案件列表（状态为 PENDING）
     *
     * @return PENDING 状态的保全读模型列表
     */
    @Transactional(readOnly = true)
    public List<MaintenanceReadModel> findPendingMaintenances(String tenantId) {
        return maintenanceViewRepository.findByTenantIdAndStatus(tenantId, MaintenanceStatus.PENDING).stream()
                .filter(MaintenanceView::isOperatorVisible)
                .map(this::toReadModel)
                .toList();
    }

    /**
     * 查询保全统计信息（管理后台看板聚合）
     * <p>
     * 汇总处理中工单数（PENDING/PROCESSING）、今日新增保全数及保全总数，按租户隔离。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 保全统计结果
     */
    @Transactional(readOnly = true)
    public MaintenanceStatisticsResult getStatistics(String tenantId) {
        long processingCount = maintenanceViewRepository.countOperatorVisibleByTenantIdAndStatusIn(tenantId,
                List.of(MaintenanceStatus.PENDING, MaintenanceStatus.PROCESSING));
        LocalDate today = LocalDate.now();
        long todayCount = maintenanceViewRepository.countOperatorVisibleByTenantIdAndCreateTimeRange(tenantId,
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long totalCount = maintenanceViewRepository.countOperatorVisibleByTenantId(tenantId);
        return new MaintenanceStatisticsResult(processingCount, todayCount, totalCount);
    }

    // ==================== 私有辅助 ====================

    /** 校验保全读模型存在并返回；不存在抛 {@link MaintenanceNotFoundException} */
    private MaintenanceView requireMaintenanceExists(String maintenanceId, String tenantId) {
        return maintenanceViewRepository.findByMaintenanceIdAndTenantId(maintenanceId, tenantId)
                .filter(MaintenanceView::isOperatorVisible)
                .orElseThrow(MaintenanceNotFoundException::new);
    }

    // 读模型 → 应用层读模型（操作人由事件投影写入读模型 created_by/updated_by 列）
    private MaintenanceReadModel toReadModel(MaintenanceView view) {
        return MaintenanceReadModel.builder()
                .maintenanceNo(view.getMaintenanceNo())
                .id(view.getMaintenanceId())
                .policyId(view.getPolicyId())
                .customerId(view.getCustomerId())
                .maintenanceType(view.getMaintenanceType() != null ? view.getMaintenanceType().getValue() : null)
                .totalAmount(view.getTotalAmount())
                .refundAmount(view.getRefundAmount())
                .premiumSettlementStatus(view.getPremiumSettlementStatus() == null
                        ? null
                        : view.getPremiumSettlementStatus().name())
                .originalCalculationId(view.getOriginalCalculationId())
                .replacementCalculationId(view.getReplacementCalculationId())
                .premiumAdjustmentId(view.getPremiumAdjustmentId())
                .premiumAdjustmentResultHash(view.getPremiumAdjustmentResultHash())
                .billingPostingId(view.getBillingPostingId())
                .refundInstructionId(view.getRefundInstructionId())
                .refundOrderId(view.getRefundOrderId())
                .refundStatus(view.getRefundStatus())
                .commissionAdjustmentCount(view.getCommissionAdjustmentCount())
                .balanceDirection(view.getBalanceDirection() == null ? null : view.getBalanceDirection().name())
                .balanceAmount(view.getBalanceAmount())
                .balanceCurrency(view.getBalanceCurrency())
                .surrenderPolicyCode(view.getSurrenderPolicyCode())
                .surrenderPolicyVersion(view.getSurrenderPolicyVersion())
                .surrenderPolicyContentHash(view.getSurrenderPolicyContentHash())
                .surrenderPolicyYear(view.getSurrenderPolicyYear())
                .coolingOffDays(view.getCoolingOffDays())
                .surrenderRefundType(view.getSurrenderRefundType())
                .withinCoolingOff(view.getWithinCoolingOff())
                .cashValueRate(view.getCashValueRate())
                .retainedCustomerAmount(view.getRetainedCustomerAmount())
                .internalCostRetentionRate(view.getInternalCostRetentionRate())
                .effectiveTimeType(view.getEffectiveTimeType() != null ? view.getEffectiveTimeType().getCode() : null)
                .specificEffectiveDate(view.getSpecificEffectiveDate())
                .description(view.getDescription())
                .status(view.getStatus() != null ? view.getStatus().getValue() : null)
                .createdAt(view.getCreateTime())
                .createdBy(view.getCreatedBy())
                .updatedAt(view.getUpdateTime())
                .updatedBy(view.getUpdatedBy())
                .tenantId(view.getTenantId())
                .build();
    }

    // 检查保单是否存在
    // 🔴 适配器已区分「不可达」与「不存在」：此处返回 false 仅代表保单域正常应答但无有效保单事实，
    // 服务不可达时适配器抛 MaintenanceRemoteCallException，不会被误判为不存在（D13）
    private void validatePolicyExists(String policyId, String tenantId) {
        if (!policyServicePort.policyExists(policyId, tenantId)) {
            throw new PolicyNotFoundException();
        }
    }

    // 验证保单状态是否符合保全类型要求
    // 🔴 准入结论收敛到 MaintenanceAdmissionMatrix（与独立建案入口同源），本方法只做「取数 → 委托」：
    //    原先的 switch 只显式覆盖 8/23 类、其余 15 类落 default **放行**（旧版建案开关默认启用 ⇒ 该放行是活跃路径，
    //    含退保/贷款/减保/部分领取，以及本该对前驱状态有硬要求的中止/恢复/终止）；且保单状态被折叠成
    //    active/reinstatable 两个布尔，无法表达复效(仅 LAPSED)/恢复(仅 SUSPENDED)/终止(三态)各自的准入集合。
    // 🔴 禁止 catch-all 归一为「保单不存在」：那会把保单服务故障伪装成数据问题（D13）。
    //    适配器已把技术故障抛为 MaintenanceRemoteCallException，此处原样上抛，由 Web 层映射 HTTP 502。
    private void validatePolicyStatusForMaintenance(String policyId, MaintenanceType maintenanceType) {
        PolicyServicePort.PolicyStatusSnapshot policyStatus = policyServicePort.getPolicyStatus(policyId);
        MaintenanceAdmissionMatrix.require(maintenanceType, policyStatus.status());
    }

    // 检查客户是否存在
    private void validateCustomer(String customerId, String tenantId) {
        if (!customerServicePort.customerExists(customerId)) {
            throw new CustomerNotFoundException();
        }
    }
}
