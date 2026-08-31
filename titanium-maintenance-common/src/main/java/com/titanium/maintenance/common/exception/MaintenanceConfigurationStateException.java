package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.IllegalStateTransitionException;

/** 保全项配置生命周期非法状态异常。 */
public class MaintenanceConfigurationStateException extends IllegalStateTransitionException {

    private static final String AGGREGATE_TYPE = "MaintenanceItemConfiguration";

    public MaintenanceConfigurationStateException(String configurationId, String fromStatus, String operation) {
        super(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_STATE_TRANSITION_INVALID,
                AGGREGATE_TYPE, configurationId, fromStatus, operation);
    }

    public MaintenanceConfigurationStateException(
            String configurationId, String fromStatus, String operation, String reason) {
        super(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_STATE_TRANSITION_INVALID,
                AGGREGATE_TYPE, configurationId, fromStatus, operation, reason);
    }
}
