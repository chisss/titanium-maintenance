package com.titanium.maintenance.application.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.maintenance.aggregate.Maintenance;
import com.titanium.maintenance.client.CustomerServiceClient;
import com.titanium.maintenance.client.PolicyServiceClient;
import com.titanium.maintenance.command.AddMaintenanceChangeCommand;
import com.titanium.maintenance.command.CalculateMaintenancePremiumCommand;
import com.titanium.maintenance.command.ChangeMaintenanceStatusCommand;
import com.titanium.maintenance.command.CreateMaintenanceCommand;
import com.titanium.maintenance.command.ExecuteMaintenanceCommand;
import com.titanium.maintenance.enums.EffectiveTimeType;
import com.titanium.maintenance.enums.MaintenanceChangeType;
import com.titanium.maintenance.enums.MaintenanceStatus;
import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.exception.CustomerNotFoundException;
import com.titanium.maintenance.exception.InvalidMaintenanceStatusException;
import com.titanium.maintenance.exception.MaintenanceTypeExcludedException;
import com.titanium.maintenance.exception.PendingMaintenanceExistsException;
import com.titanium.maintenance.exception.PolicyNotActiveException;
import com.titanium.maintenance.exception.PolicyNotFoundException;
import com.titanium.maintenance.exception.PolicyNotTerminatedException;
import com.titanium.maintenance.repository.MaintenanceRepository;
import com.titanium.maintenance.service.MaintenanceService;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;

@Service
@Transactional
public class MaintenanceApplicationService {
    private final CommandGateway        commandGateway;
    private final MaintenanceService    maintenanceService;
    private final MaintenanceRepository maintenanceRepository;
    private final PolicyServiceClient   policyServiceClient;
    private final CustomerServiceClient customerServiceClient;

    public MaintenanceApplicationService(CommandGateway commandGateway, MaintenanceService maintenanceService,
                                         MaintenanceRepository maintenanceRepository,
                                         PolicyServiceClient policyServiceClient,
                                         CustomerServiceClient customerServiceClient) {
        this.commandGateway = commandGateway;
        this.maintenanceService = maintenanceService;
        this.maintenanceRepository = maintenanceRepository;
        this.policyServiceClient = policyServiceClient;
        this.customerServiceClient = customerServiceClient;
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
        return commandGateway.send(command).thenApply(result -> command.getId().getId());
    }

    // 添加保全变更记录
    public CompletableFuture<String> addMaintenanceChange(String maintenanceId, String changeType, String fieldName,
                                                          String oldValue, String newValue, String createdBy) {
        // 验证保全记录存在
        maintenanceService.findMaintenanceById(MaintenanceId.of(maintenanceId));

        // 创建命令（外部传入 code 字符串，在边界转换为强类型枚举）
        AddMaintenanceChangeCommand command = AddMaintenanceChangeCommand.builder().id(MaintenanceId.of(maintenanceId))
                .changeType(MaintenanceChangeType.fromCode(changeType)).fieldName(fieldName).oldValue(oldValue)
                .newValue(newValue).createdBy(createdBy).build();

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
        CalculateMaintenancePremiumCommand command = CalculateMaintenancePremiumCommand.builder()
                .id(MaintenanceId.of(maintenanceId)).totalAmount(totalAmount).refundAmount(refundAmount)
                .calculationDetails(calculationDetails).updatedBy(updatedBy).build();

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
        ExecuteMaintenanceCommand command = ExecuteMaintenanceCommand.builder().id(MaintenanceId.of(maintenanceId))
                .effectiveTime(effectiveTime).executionDetails(executionDetails).updatedBy(updatedBy).build();

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

    // 根据ID查询保全记录
    @Transactional(readOnly = true)
    public Maintenance findMaintenanceById(String id) {
        return maintenanceService.findMaintenanceById(MaintenanceId.of(id));
    }

    // 根据保单ID查询保全记录
    @Transactional(readOnly = true)
    public List<Maintenance> findMaintenancesByPolicyId(String policyId) {
        return maintenanceService.findMaintenancesByPolicyId(PolicyId.of(policyId));
    }

    // 检查保单是否存在
    private void validatePolicyExists(String policyId, String tenantId) {
        try {
            policyServiceClient.getPolicyById(policyId, tenantId);
        } catch (Exception e) {
            throw new PolicyNotFoundException();
        }
    }

    // 验证保单状态是否符合保全类型要求
    private void validatePolicyStatusForMaintenance(String policyId, MaintenanceType maintenanceType, String tenantId) {
        try {
            var policyStatus = policyServiceClient.getPolicyStatus(policyId, tenantId);

            switch (maintenanceType) {
                case POLICY_REINSTATEMENT:
                    // 只有失效状态的保单才可以做复效
                    if (!policyStatus.isTerminated()) {
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
                    if (!policyStatus.isActive()) {
                        throw new PolicyNotActiveException();
                    }
                    break;
                default:
                    // 其他保全类型的验证
                    break;
            }
        } catch (Exception e) {
            if (e instanceof PolicyNotTerminatedException || e instanceof PolicyNotActiveException) {
                throw e;
            }
            throw new PolicyNotFoundException();
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
        try {
            customerServiceClient.getCustomerById(customerId, tenantId);
        } catch (Exception e) {
            throw new CustomerNotFoundException();
        }
    }
}
