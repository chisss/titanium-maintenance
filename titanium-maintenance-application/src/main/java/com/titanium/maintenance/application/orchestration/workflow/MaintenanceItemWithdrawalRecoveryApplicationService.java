package com.titanium.maintenance.application.orchestration.workflow;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.command.withdrawal.MaintenanceItemWithdrawalInput;
import com.titanium.maintenance.application.configuration.MaintenanceWithdrawalRecoveryProperties;
import com.titanium.maintenance.port.maintenance.MaintenanceItemWithdrawalRecoveryLeasePort;
import com.titanium.maintenance.port.maintenance.MaintenanceItemWithdrawalRecoveryLeasePort.WithdrawalRecoveryLease;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 以数据库租约驱动项目撤销失败和原资金等待状态的自动恢复。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceItemWithdrawalRecoveryApplicationService {

    private static final String RECOVERY_OPERATOR = "maintenance-withdrawal-recovery";

    private final MaintenanceItemWithdrawalApplicationService withdrawalApplicationService;
    private final MaintenanceItemWithdrawalRecoveryLeasePort leasePort;
    private final MaintenanceWithdrawalRecoveryProperties properties;

    private final String recoveryOwner = "withdrawal-recovery:" + UUID.randomUUID();

    public void executeDue() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<WithdrawalRecoveryLease> leases = leasePort.acquireDue(
                recoveryOwner, now, now.minus(properties.getRetryDelay()),
                now.plus(properties.getLeaseDuration()), properties.getBatchSize(), properties.getMaxAttempts());
        for (WithdrawalRecoveryLease lease : leases) {
            recover(lease);
        }
    }

    private void recover(WithdrawalRecoveryLease lease) {
        try {
            withdrawalApplicationService.withdraw(new MaintenanceItemWithdrawalInput(
                    lease.maintenanceId(), lease.itemCode(), lease.operationId(), lease.reason(),
                    lease.paymentMethod(), RECOVERY_OPERATOR, lease.tenantId())).join();
        } catch (RuntimeException exception) {
            log.warn("项目撤销自动恢复失败: maintenanceId={}, itemCode={}, retryCount={}, error={}",
                    lease.maintenanceId(), lease.itemCode(), lease.retryCount(), safeMessage(exception));
        } finally {
            leasePort.release(lease.itemViewId(), recoveryOwner);
        }
    }

    private String safeMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
