package com.titanium.maintenance.common.exception;


import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/** 旧版整案执行写入口已通过部署开关关闭。 */
public class MaintenanceLegacyExecutionDisabledException extends BusinessException {

    public MaintenanceLegacyExecutionDisabledException() {
        super("旧版保全执行入口当前未启用，请使用独立保全案件的任务级生效流程",
                MaintenanceErrorCode.MAINTENANCE_LEGACY_EXECUTION_DISABLED);
    }
}
