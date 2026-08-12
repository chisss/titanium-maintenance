package com.titanium.maintenance.valueobject;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceChangeType;

/**
 * 保全变更记录值对象。
 *
 * @param changeType 变更类型
 * @param fieldName 变更字段名
 * @param oldValue 变更前值
 * @param newValue 变更后值
 * @param createdAt 变更记录创建时间
 */
public record MaintenanceChange(MaintenanceChangeType changeType, String fieldName, String oldValue,
                                String newValue, LocalDateTime createdAt) {
}
