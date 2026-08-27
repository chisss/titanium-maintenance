package com.titanium.maintenance.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.application.model.MaintenanceReadModel;
import com.titanium.maintenance.application.model.MaintenanceSearchPageResult;
import com.titanium.maintenance.application.model.MaintenanceStatisticsResult;
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
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceTypeExcludedException;
import com.titanium.maintenance.common.exception.PendingMaintenanceExistsException;
import com.titanium.maintenance.common.exception.PolicyNotActiveException;
import com.titanium.maintenance.common.exception.PolicyNotFoundException;
import com.titanium.maintenance.common.exception.PolicyNotTerminatedException;
import com.titanium.maintenance.exception.MaintenanceLegacyCreationDisabledException;
import com.titanium.maintenance.exception.MaintenanceLegacyExecutionDisabledException;
import com.titanium.maintenance.exception.MaintenanceLegacyPremiumCalculationDisabledException;
import com.titanium.maintenance.port.CustomerServicePort;
import com.titanium.maintenance.port.MaintenanceLegacyCreationFeaturePort;
import com.titanium.maintenance.port.MaintenanceLegacyExecutionFeaturePort;
import com.titanium.maintenance.port.MaintenanceLegacyPremiumCalculationFeaturePort;
import com.titanium.maintenance.port.PolicyServicePort;
import com.titanium.maintenance.query.repository.MaintenanceViewRepository;
import com.titanium.maintenance.query.view.MaintenanceView;
import com.titanium.maintenance.repository.MaintenanceExclusionRepository;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * 保全应用服务（读写用例入口门面）
 * <p>
 * 写入口：跨域校验（保单/客户存在性、保单状态、在途/互斥）后经 {@link CommandGateway} 发命令，业务规则内聚在
 * {@code Maintenance} 聚合根。写侧收敛为纯事件溯源后，存在性/在途校验统一走 CQRS 读模型
 * {@link MaintenanceViewRepository}（表 {@code t_maintenance_view}，最终一致），不再读写侧状态表。
 * 互斥规则查配置端口 {@link MaintenanceExclusionRepository}（参考数据）。
 * </p>
 */
@Service
@Transactional
public class MaintenanceApplicationService {

    private final CommandGateway                 commandGateway;
    private final MaintenanceViewRepository       maintenanceViewRepository;
    private final MaintenanceExclusionRepository  maintenanceExclusionRepository;
    private final PolicyServicePort               policyServicePort;
    private final CustomerServicePort             customerServicePort;
    private final MaintenanceLegacyCreationFeaturePort legacyCreationFeaturePort;
    private final MaintenanceLegacyPremiumCalculationFeaturePort legacyPremiumCalculationFeaturePort;
    private final MaintenanceLegacyExecutionFeaturePort legacyExecutionFeaturePort;

    public MaintenanceApplicationService(CommandGateway commandGateway,
                                         MaintenanceViewRepository maintenanceViewRepository,
                                         MaintenanceExclusionRepository maintenanceExclusionRepository,
                                         PolicyServicePort policyServicePort,
                                         CustomerServicePort customerServicePort,
                                         MaintenanceLegacyCreationFeaturePort legacyCreationFeaturePort,
                                         MaintenanceLegacyPremiumCalculationFeaturePort legacyPremiumCalculationFeaturePort,
                                         MaintenanceLegacyExecutionFeaturePort legacyExecutionFeaturePort) {
        this.commandGateway = commandGateway;
        this.maintenanceViewRepository = maintenanceViewRepository;
        this.maintenanceExclusionRepository = maintenanceExclusionRepository;
        this.policyServicePort = policyServicePort;
        this.customerServicePort = customerServicePort;
        this.legacyCreationFeaturePort = legacyCreationFeaturePort;
        this.legacyPremiumCalculationFeaturePort = legacyPremiumCalculationFeaturePort;
        this.legacyExecutionFeaturePort = legacyExecutionFeaturePort;
    }

    // 创建保全案件
    public CompletableFuture<String> createMaintenanceCase(String policyId, String customerId,
                                                           MaintenanceType maintenanceType,
                                                           EffectiveTimeType effectiveTimeType,
                                                           LocalDateTime specificEffectiveDate, String description,
                                                           String createdBy, String tenantId) {
        if (!legacyCreationFeaturePort.isEnabled(tenantId)) {
            throw new MaintenanceLegacyCreationDisabledException();
        }
        // 验证保单存在
        validatePolicyExists(policyId, tenantId);
        // 验证客户存在
        validateCustomer(customerId, tenantId);
        // 验证保单状态是否符合保全类型要求
        validatePolicyStatusForMaintenance(policyId, maintenanceType, tenantId);
        // 检查是否存在在途保全案件
        checkPendingMaintenance(policyId, tenantId);
        // 检查保全项互斥性
        checkMaintenanceExclusion(policyId, maintenanceType, tenantId);

        CreateMaintenanceCommand command = CreateMaintenanceCommand.of(policyId, customerId, maintenanceType,
                effectiveTimeType, specificEffectiveDate, description, createdBy, tenantId);
        return commandGateway.send(command).thenApply(result -> command.id().id());
    }

    // 添加保全变更记录
    public CompletableFuture<String> addMaintenanceChange(String maintenanceId, String changeType, String fieldName,
                                                          String oldValue, String newValue, String createdBy,
                                                          String tenantId) {
        requireMaintenanceExists(maintenanceId, tenantId);
        AddMaintenanceChangeCommand command = new AddMaintenanceChangeCommand(MaintenanceId.of(maintenanceId),
                MaintenanceChangeType.fromCode(changeType), fieldName, oldValue, newValue, createdBy);
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 计算保全保费
    public CompletableFuture<String> calculateMaintenancePremium(String maintenanceId, BigDecimal totalAmount,
                                                                 BigDecimal refundAmount, String calculationDetails,
                                                                 String updatedBy, String tenantId) {
        if (!legacyPremiumCalculationFeaturePort.isEnabled(tenantId)) {
            throw new MaintenanceLegacyPremiumCalculationDisabledException();
        }
        requireMaintenanceExists(maintenanceId, tenantId);
        CalculateMaintenancePremiumCommand command = new CalculateMaintenancePremiumCommand(
                MaintenanceId.of(maintenanceId), totalAmount, refundAmount, calculationDetails, updatedBy);
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 执行保全
    public CompletableFuture<String> executeMaintenance(String maintenanceId, LocalDateTime effectiveTime,
                                                        String executionDetails, String updatedBy, String tenantId) {
        if (!legacyExecutionFeaturePort.isEnabled(tenantId)) {
            throw new MaintenanceLegacyExecutionDisabledException();
        }
        MaintenanceView view = requireMaintenanceExists(maintenanceId, tenantId);
        // 验证保全状态（读模型最终一致）
        if (view.getStatus() != MaintenanceStatus.APPROVED) {
            throw new InvalidMaintenanceStatusException();
        }
        ExecuteMaintenanceCommand command = new ExecuteMaintenanceCommand(MaintenanceId.of(maintenanceId),
                effectiveTime, executionDetails, updatedBy);
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 变更保全记录状态
    public CompletableFuture<String> changeMaintenanceStatus(String maintenanceId, MaintenanceStatus newStatus,
                                                             String changeReason, String changedBy, String tenantId) {
        requireMaintenanceExists(maintenanceId, tenantId);
        ChangeMaintenanceStatusCommand command = ChangeMaintenanceStatusCommand.of(maintenanceId, newStatus,
                changeReason, changedBy);
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
    private void validatePolicyExists(String policyId, String tenantId) {
        if (!policyServicePort.policyExists(policyId, tenantId)) {
            throw new PolicyNotFoundException();
        }
    }

    // 验证保单状态是否符合保全类型要求
    private void validatePolicyStatusForMaintenance(String policyId, MaintenanceType maintenanceType, String tenantId) {
        PolicyServicePort.PolicyStatusSnapshot policyStatus;
        try {
            policyStatus = policyServicePort.getPolicyStatus(policyId, tenantId);
        } catch (Exception e) {
            throw new PolicyNotFoundException();
        }

        switch (maintenanceType) {
            case POLICY_REINSTATEMENT:
                // 仅失效(LAPSED)保单可复效：与保单域状态机 LAPSED→EFFECTIVE 对齐，终态不可复效
                if (!policyStatus.reinstatable()) {
                    throw new PolicyNotTerminatedException();
                }
                break;
            case SUBJECT_CHANGE:
            case POLICY_INFO_CHANGE:
            case POLICY_PERIOD_CHANGE:
            case COVERAGE_AMOUNT_CHANGE:
            case INSURED_INFO_CHANGE:
            case SMOKING_STATUS_CHANGE:
            case COVERAGE_CHANGE:
                // 只有生效的保单才可以做这些保全
                if (!policyStatus.active()) {
                    throw new PolicyNotActiveException();
                }
                break;
            default:
                // 其他保全类型无需额外的保单状态校验
                break;
        }
    }

    // 检查是否存在在途保全案件（读模型：PENDING/PROCESSING/APPROVED 视为在途）
    private void checkPendingMaintenance(String policyId, String tenantId) {
        if (!findInFlightMaintenances(policyId, tenantId).isEmpty()) {
            throw new PendingMaintenanceExistsException();
        }
    }

    // 检查保全项互斥性
    private void checkMaintenanceExclusion(String policyId, MaintenanceType maintenanceType, String tenantId) {
        for (MaintenanceView view : findInFlightMaintenances(policyId, tenantId)) {
            if (maintenanceExclusionRepository.isMaintenanceTypeExcluded(view.getMaintenanceType(), maintenanceType,
                    tenantId)) {
                throw new MaintenanceTypeExcludedException();
            }
        }
    }

    // 查询保单在途保全（读模型最终一致）
    private List<MaintenanceView> findInFlightMaintenances(String policyId, String tenantId) {
        return maintenanceViewRepository.findByPolicyIdAndTenantIdAndStatusIn(policyId, tenantId,
                List.of(MaintenanceStatus.PENDING, MaintenanceStatus.PROCESSING, MaintenanceStatus.APPROVED)).stream()
                .filter(MaintenanceView::isOperatorVisible)
                .toList();
    }

    // 检查客户是否存在
    private void validateCustomer(String customerId, String tenantId) {
        if (!customerServicePort.customerExists(customerId, tenantId)) {
            throw new CustomerNotFoundException();
        }
    }
}
