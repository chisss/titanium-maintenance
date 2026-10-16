package com.titanium.maintenance.infrastructure.config;

import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.modelling.command.Repository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.titanium.maintenance.aggregate.Maintenance;

/**
 * Axon Framework 配置类
 * <p>
 * 单体应用模式下，命令总线/命令网关/事件存储均由 axon-spring-boot-starter 自动装配， 此处仅显式声明保全聚合根的事件溯源仓库。
 * </p>
 */
@Configuration
public class AxonConfig {

    /**
     * 快照触发阈值：聚合累计事件数达到该值即生成快照。
     * <p>
     * 阈值依据 2026-10-15 对本域事件流的实测分布确定：单聚合事件数最多 37、均值约 12，达到 50 的聚合为 0， 且快照表从未写入（0
     * 行）。取 50 使存量数据零快照开销，同时为聚合事件流持续增长提供兜底——越过阈值后， 聚合加载只需「最新快照 + 增量事件」，不再重放完整事件流。
     * </p>
     */
    private static final int SNAPSHOT_TRIGGER_THRESHOLD = 50;

    /**
     * 保全聚合根的按事件数计数的快照触发器。
     * <p>
     * 🔴 触发器必须挂在下方 {@link #maintenanceRepository} 的 builder 上。本域以显式 {@code Repository<Maintenance>}
     * Bean 装配仓库，Axon 的 Spring 集成直接复用该 Bean 作为聚合仓库，{@code @Aggregate} 注解上的
     * {@code snapshotTriggerDefinition} 属性在这条装配路径上不生效。若将来移除本 Bean 改由注解装配， 须同步将注解属性指向本 Bean
     * 名，否则快照将静默失效（无报错、无日志、无快照）。
     * </p>
     */
    @Bean
    public SnapshotTriggerDefinition maintenanceSnapshotTriggerDefinition(Snapshotter snapshotter) {
        return new EventCountSnapshotTriggerDefinition(snapshotter, SNAPSHOT_TRIGGER_THRESHOLD);
    }

    /**
     * 配置保全聚合根的事件溯源仓库（挂载快照触发器）
     */
    @Bean
    public Repository<Maintenance> maintenanceRepository(EventStore eventStore,
            SnapshotTriggerDefinition maintenanceSnapshotTriggerDefinition) {
        return EventSourcingRepository.builder(Maintenance.class)
                .eventStore(eventStore)
                .snapshotTriggerDefinition(maintenanceSnapshotTriggerDefinition)
                .build();
    }
}
