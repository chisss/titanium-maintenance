package com.titanium.maintenance.application.scheduled;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.application.orchestration.workflow.MaintenanceEffectScheduleApplicationService;

import lombok.RequiredArgsConstructor;

/** 周期扫描到期计划，实际并发互斥由数据库租约保证。 */
@Component
@RequiredArgsConstructor
public class MaintenanceEffectScheduler {

    private final MaintenanceEffectScheduleApplicationService scheduleApplicationService;

    @Scheduled(
            fixedDelayString = "${titanium.maintenance.scheduling.poll-interval:PT30S}",
            initialDelayString = "${titanium.maintenance.scheduling.initial-delay:PT30S}")
    public void executeDueSchedules() {
        scheduleApplicationService.executeDue();
    }
}
