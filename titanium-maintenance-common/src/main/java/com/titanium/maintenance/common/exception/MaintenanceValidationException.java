package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.CommandValidationException;

/**
 * 保全命令校验异常
 * <p>
 * 当保全领域的命令参数或值对象校验失败时抛出，如保全金额为负、保全类型/状态非法等。 继承 metadata 统一异常体系的
 * {@link CommandValidationException}， 并携带保全域标准错误码 {@link MaintenanceErrorCode#MAINTENANCE_COMMAND_VALIDATION_FAILED}
 * 及命令名、字段名等上下文， 替代领域层中裸抛的 {@code IllegalArgumentException}。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class MaintenanceValidationException extends CommandValidationException {

    /**
     * 构造保全校验异常（统一携带 71 段标准错误码）
     *
     * @param commandName 命令名（或值对象名）
     * @param validationMessage 校验失败描述
     */
    public MaintenanceValidationException(String commandName, String validationMessage) {
        super(MaintenanceErrorCode.MAINTENANCE_COMMAND_VALIDATION_FAILED, commandName, validationMessage);
    }

    /**
     * 构造保全校验异常（含字段名，统一携带 71 段标准错误码）
     *
     * @param commandName 命令名（或值对象名）
     * @param fieldName 校验失败字段名
     * @param validationMessage 校验失败描述
     */
    public MaintenanceValidationException(String commandName, String fieldName, String validationMessage) {
        super(MaintenanceErrorCode.MAINTENANCE_COMMAND_VALIDATION_FAILED, commandName, fieldName, validationMessage);
    }
}
