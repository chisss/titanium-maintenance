package com.titanium.maintenance.common.exception;


import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/** 客户端提交的配置版本已过期。 */
public class MaintenanceConfigurationPreconditionFailedException extends BusinessException {

    public MaintenanceConfigurationPreconditionFailedException() {
        super("配置已被其他操作更新，请刷新后重试",
                MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_PRECONDITION_FAILED);
    }
}
