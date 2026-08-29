package com.titanium.maintenance.valueobject;

import com.titanium.common.util.SnowflakeIdGenerator;

/**
 * 保全案件标识值对象（Axon 聚合根标识）。
 * <p>
 * 作为 {@code @AggregateIdentifier} / {@code @TargetAggregateIdentifier} 使用，
 * Axon 依赖其 {@link #toString()} 生成路由键与事件存储标识，故必须返回裸 id 值。
 * </p>
 *
 * @param id 保全案件唯一标识
 */
public record MaintenanceId(String id) {

    public static MaintenanceId generate() {
        return new MaintenanceId(SnowflakeIdGenerator.generate());
    }

    public static MaintenanceId of(String id) {
        return new MaintenanceId(id);
    }

    // 🔴 Axon 聚合标识：toString 必须返回裸 id，否则命令路由 / 事件存储标识错乱
    @Override
    public String toString() {
        return id;
    }
}
