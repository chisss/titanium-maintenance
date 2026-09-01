package com.titanium.maintenance.port.tenant;

/** 解析租户业务时区，调度计划必须冻结解析结果。 */
public interface TenantTimeZonePort {

    String resolveZoneId(String tenantId);
}
