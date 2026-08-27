package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 重试成功后关闭补偿处理标记，原补偿事实继续保留审计。 */
public record MaintenanceEffectCompensationResolvedEvent(
        MaintenanceId maintenanceId,
        String compensationId,
        String endorsementNo,
        LocalDateTime resolvedAt,
        String resolvedBy,
        String tenantId) {
}
