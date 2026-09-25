package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/** 客户端提交的配置版本已过期。 */
public class MaintenanceConfigurationPreconditionFailedException extends DomainException {

    public MaintenanceConfigurationPreconditionFailedException() {
        super(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_PRECONDITION_FAILED, "配置已被其他操作更新，请刷新后重试");
    }
}
