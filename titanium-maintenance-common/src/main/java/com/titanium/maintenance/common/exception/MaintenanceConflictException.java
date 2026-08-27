package com.titanium.maintenance.common.exception;

/** 保全请求与当前资源状态发生并发或顺序冲突时使用的领域异常。 */
public class MaintenanceConflictException extends MaintenanceValidationException {

    public MaintenanceConflictException(String commandName, String fieldName, String message) {
        super(commandName, fieldName, message);
    }
}
