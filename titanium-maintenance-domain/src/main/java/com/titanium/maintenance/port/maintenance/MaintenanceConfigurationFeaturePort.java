package com.titanium.maintenance.port.maintenance;

/** 保全配置灰度开关端口。 */
public interface MaintenanceConfigurationFeaturePort {

    /** 判断指定租户是否允许创建或发布保全配置。 */
    boolean isWriteEnabled(String tenantId);
}
