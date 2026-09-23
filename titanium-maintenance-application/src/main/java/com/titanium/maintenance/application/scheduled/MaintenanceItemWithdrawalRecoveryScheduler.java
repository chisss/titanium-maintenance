package com.titanium.maintenance.application.scheduled;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.application.orchestration.workflow.MaintenanceItemWithdrawalRecoveryApplicationService;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/** 周期恢复项目撤销补偿失败和原资金等待案件。 */
@Component
@RequiredArgsConstructor
public class MaintenanceItemWithdrawalRecoveryScheduler {

    private final MaintenanceItemWithdrawalRecoveryApplicationService recoveryApplicationService;

        @SchedulerLock(name = "maintenance-recoverDueWithdrawals")
    @Scheduled(
            fixedDelayString = "${titanium.maintenance.withdrawal-recovery.poll-interval:PT30S}",
            initialDelayString = "${titanium.maintenance.withdrawal-recovery.initial-delay:PT30S}")
    public void recoverDueWithdrawals() {
        recoveryApplicationService.executeDue();
    }
}
