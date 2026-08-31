package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.CommandValidationException;

/** 保全请求与当前资源状态发生并发或顺序冲突时使用的领域异常（HTTP 409）。 */
public class MaintenanceConflictException extends CommandValidationException {

    public MaintenanceConflictException(String commandName, String fieldName, String message) {
        super(MaintenanceErrorCode.MAINTENANCE_CONFLICT, commandName, fieldName, message);
    }
}
