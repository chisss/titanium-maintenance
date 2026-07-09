package com.titanium.maintenance.application.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.aggregate.Maintenance;
import com.titanium.maintenance.application.dto.MaintenanceResponseDTO;
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
import com.titanium.maintenance.common.exception.MaintenanceTypeExcludedException;
import com.titanium.maintenance.common.exception.PendingMaintenanceExistsException;
import com.titanium.maintenance.common.exception.PolicyNotActiveException;
import com.titanium.maintenance.common.exception.PolicyNotFoundException;
import com.titanium.maintenance.common.exception.PolicyNotTerminatedException;
import com.titanium.maintenance.port.CustomerServicePort;
import com.titanium.maintenance.port.PolicyServicePort;
import com.titanium.maintenance.service.MaintenanceService;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

@Service
@Transactional
public class MaintenanceApplicationService {
    private final CommandGateway      commandGateway;
    private final MaintenanceService  maintenanceService;
    private final PolicyServicePort   policyServicePort;
    private final CustomerServicePort customerServicePort;

    public MaintenanceApplicationService(CommandGateway commandGateway, MaintenanceService maintenanceService,
                                         PolicyServicePort policyServicePort,
                                         CustomerServicePort customerServicePort) {
        this.commandGateway = commandGateway;
        this.maintenanceService = maintenanceService;
        this.policyServicePort = policyServicePort;
        this.customerServicePort = customerServicePort;
    }

    // 创建保全案件
    public CompletableFuture<String> createMaintenanceCase(String policyId, String customerId,
                                                           MaintenanceType maintenanceType,
                                                           EffectiveTimeType effectiveTimeType,
                                                           LocalDateTime specificEffectiveDate, String description,
                                                           String createdBy, String tenantId) {
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

        // 创建命令
        CreateMaintenanceCommand command = CreateMaintenanceCommand.of(policyId, customerId, maintenanceType,
                effectiveTimeType, specificEffectiveDate, description, createdBy, tenantId);

        // 发送命令到Axon Command Gateway
        return commandGateway.send(command).thenApply(result -> command.id().getId());
    }

    // 添加保全变更记录
    public CompletableFuture<String> addMaintenanceChange(String maintenanceId, String changeType, String fieldName,
                                                          String oldValue, String newValue, String createdBy) {
        // 验证保全记录存在
        maintenanceService.findMaintenanceById(MaintenanceId.of(maintenanceId));

        // 创建命令（外部传入 code 字符串，在边界转换为强类型枚举）
        AddMaintenanceChangeCommand command = new AddMaintenanceChangeCommand(MaintenanceId.of(maintenanceId),
                MaintenanceChangeType.fromCode(changeType), fieldName, oldValue, newValue, createdBy);

        // 发送命令到Axon Command Gateway
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 计算保全保费
    public CompletableFuture<String> calculateMaintenancePremium(String maintenanceId, BigDecimal totalAmount,
                                                                 BigDecimal refundAmount, String calculationDetails,
                                                                 String updatedBy) {
        // 验证保全记录存在
        maintenanceService.findMaintenanceById(MaintenanceId.of(maintenanceId));

        // 创建命令
        CalculateMaintenancePremiumCommand command = new CalculateMaintenancePremiumCommand(
                MaintenanceId.of(maintenanceId), totalAmount, refundAmount, calculationDetails, updatedBy);

        // 发送命令到Axon Command Gateway
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 执行保全
    public CompletableFuture<String> executeMaintenance(String maintenanceId, LocalDateTime effectiveTime,
                                                        String executionDetails, String updatedBy) {
        // 验证保全记录存在
        Maintenance maintenance = maintenanceService.findMaintenanceById(MaintenanceId.of(maintenanceId));

        // 验证保全状态
        if (maintenance.getStatus() != MaintenanceStatus.APPROVED) {
            throw new InvalidMaintenanceStatusException();
        }

        // 创建命令
        ExecuteMaintenanceCommand command = new ExecuteMaintenanceCommand(MaintenanceId.of(maintenanceId),
                effectiveTime, executionDetails, updatedBy);

        // 发送命令到Axon Command Gateway
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 变更保全记录状态
    public CompletableFuture<String> changeMaintenanceStatus(String maintenanceId, MaintenanceStatus newStatus,
                                                             String changeReason, String changedBy) {
        // 验证保全记录存在
        maintenanceService.findMaintenanceById(MaintenanceId.of(maintenanceId));

        // 创建命令
        ChangeMaintenanceStatusCommand command = ChangeMaintenanceStatusCommand.of(maintenanceId, newStatus,
                changeReason, changedBy);

        // 发送命令到Axon Command Gateway
        return commandGateway.send(command).thenApply(result -> maintenanceId);
    }

    // 根据ID查询保全记录（返回应用层 DTO，聚合根不越出应用层）
    @Transactional(readOnly = true)
    public MaintenanceResponseDTO findMaintenanceById(String id) {
        return toResponse(maintenanceService.findMaintenanceById(MaintenanceId.of(id)));
    }

    // 根据保单ID查询保全记录（返回应用层 DTO，聚合根不越出应用层）
    @Transactional(readOnly = true)
    public List<MaintenanceResponseDTO> findMaintenancesByPolicyId(String policyId) {
        return maintenanceService.findMaintenancesByPolicyId(PolicyId.of(policyId)).stream()
                .map(this::toResponse)
                .toList();
    }

    // 领域聚合根 → 应用层响应 DTO（读用例展示组装，枚举以 name() 承载，避免领域模型泄漏到 HTTP 边界）
    private MaintenanceResponseDTO toResponse(Maintenance maintenance) {
        return MaintenanceResponseDTO.builder()
                .id(maintenance.getId().getId())
                .policyId(maintenance.getPolicyId().getId())
                .customerId(maintenance.getCustomerId().getId())
                .maintenanceType(maintenance.getMaintenanceType() != null ? maintenance.getMaintenanceType().getValue() : null)
                .totalAmount(maintenance.getTotalAmount())
                .refundAmount(maintenance.getRefundAmount())
                .effectiveTimeType(maintenance.getEffectiveTimeType() != null ? maintenance.getEffectiveTimeType().getCode() : null)
                .specificEffectiveDate(maintenance.getSpecificEffectiveDate())
                .description(maintenance.getDescription())
                .status(maintenance.getStatus() != null ? maintenance.getStatus().getValue() : null)
                .createdAt(maintenance.getCreateTime())
                .createdBy(maintenance.getCreatedBy())
                .updatedAt(maintenance.getUpdateTime())
                .updatedBy(maintenance.getUpdatedBy())
                .tenantId(maintenance.getTenantId())
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
                // 仅失效(LAPSED)保单可复效：与保单域状态机 LAPSED→EFFECTIVE 对齐，
                // 终态 TERMINATED/EXPIRED 不可复效
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

    // 检查是否存在在途保全案件
    private void checkPendingMaintenance(String policyId, String tenantId) {
        List<Maintenance> pendingMaintenances = maintenanceService
                .findPendingMaintenancesByPolicyId(PolicyId.of(policyId));
        if (!pendingMaintenances.isEmpty()) {
            throw new PendingMaintenanceExistsException();
        }
    }

    // 检查保全项互斥性
    private void checkMaintenanceExclusion(String policyId, MaintenanceType maintenanceType, String tenantId) {
        List<Maintenance> pendingMaintenances = maintenanceService
                .findPendingMaintenancesByPolicyId(PolicyId.of(policyId));
        for (Maintenance maintenance : pendingMaintenances) {
            if (maintenanceService.isMaintenanceTypeExcluded(maintenance.getMaintenanceType(), maintenanceType,
                    tenantId)) {
                throw new MaintenanceTypeExcludedException();
            }
        }
    }

    // 检查客户是否存在
    private void validateCustomer(String customerId, String tenantId) {
        if (!customerServicePort.customerExists(customerId, tenantId)) {
            throw new CustomerNotFoundException();
        }
    }
}
