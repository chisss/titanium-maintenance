package com.titanium.maintenance.infrastructure.adapter.maintenance.config;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.port.tenant.TenantTimeZonePort;

import lombok.Getter;
import lombok.Setter;

/** 从服务配置解析租户业务时区；计划创建后冻结结果，不受后续配置漂移影响。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "titanium.maintenance.scheduling")
public class ConfiguredTenantTimeZoneAdapter implements TenantTimeZonePort {

    private String defaultZoneId = "Asia/Shanghai";
    private Map<String, String> tenantZoneIds = new HashMap<>();

    @Override
    public String resolveZoneId(String tenantId) {
        String configured = tenantZoneIds.getOrDefault(tenantId, defaultZoneId);
        return ZoneId.of(configured).getId();
    }
}
