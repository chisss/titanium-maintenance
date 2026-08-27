package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.exception.BusinessException;

/** 旧版整案执行写入口已通过部署开关关闭。 */
public class MaintenanceLegacyExecutionDisabledException extends BusinessException {

    public MaintenanceLegacyExecutionDisabledException() {
        super("旧版保全执行入口当前未启用，请使用独立保全案件的任务级生效流程",
                "MAINTENANCE_LEGACY_EXECUTION_DISABLED", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
