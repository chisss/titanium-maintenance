package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.exception.BusinessException;

/** 客户端提交的配置版本已过期。 */
public class MaintenanceConfigurationPreconditionFailedException extends BusinessException {

    public MaintenanceConfigurationPreconditionFailedException() {
        super("配置已被其他操作更新，请刷新后重试",
                "MAINTENANCE_CONFIGURATION_PRECONDITION_FAILED", HttpStatus.PRECONDITION_FAILED);
    }
}
