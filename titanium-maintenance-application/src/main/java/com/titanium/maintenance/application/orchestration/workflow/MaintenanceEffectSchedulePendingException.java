package com.titanium.maintenance.application.orchestration.workflow;

/** 计划已到期但案件前置流程尚未完成，可在退避后重新执行。 */
final class MaintenanceEffectSchedulePendingException extends RuntimeException {

    MaintenanceEffectSchedulePendingException(String message) {
        super(message);
    }
}
