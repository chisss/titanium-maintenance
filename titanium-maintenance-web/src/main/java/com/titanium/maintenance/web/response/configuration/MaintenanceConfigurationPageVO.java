package com.titanium.maintenance.web.response.configuration;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;

/** 配置管理列表分页响应。 */
public record MaintenanceConfigurationPageVO(
        List<ItemVO> items, long total, int page, int size, int totalPages) {

    public record ItemVO(
            String configurationId,
            String itemCode,
            String configurationVersion,
            String name,
            int stepCount,
            MaintenanceFeeMode feeMode,
            MaintenanceItemConfigurationStatus status,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            String contentHash,
            long rowVersion,
            LocalDateTime updatedAt) {
    }
}
