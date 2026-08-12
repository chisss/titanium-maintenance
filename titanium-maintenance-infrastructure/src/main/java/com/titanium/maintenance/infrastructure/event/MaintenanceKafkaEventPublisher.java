package com.titanium.maintenance.infrastructure.event;

import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.maintenance.event.MaintenanceChangeAddedEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceExecutedEvent;
import com.titanium.maintenance.event.MaintenancePremiumCalculatedEvent;
import com.titanium.maintenance.event.MaintenanceStatusChangedEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 保全域领域事件 Kafka 发布器
 * <p>
 * 订阅 Axon 领域事件并转发至 Kafka，供跨域（billing/payment 等）异步消费。仅承担消息发布，
 * 不维护读模型（读模型投影由 query 侧 {@code MaintenanceProjectionEventHandler} 负责）。
 * </p>
 */
@Component
@Slf4j
public class MaintenanceKafkaEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MaintenanceKafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @EventHandler
    public void handle(MaintenanceCreatedEvent event) {
        log.info("Handling MaintenanceCreatedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_CREATED, event.maintenanceId().id(), event);
    }

    @EventHandler
    public void handle(MaintenanceStatusChangedEvent event) {
        log.info("Handling MaintenanceStatusChangedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_STATUS_CHANGED, event.maintenanceId().id(), event);
    }

    @EventHandler
    public void handle(MaintenanceChangeAddedEvent event) {
        log.info("Handling MaintenanceChangeAddedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_CHANGE_ADDED, event.maintenanceId().id(), event);
    }

    @EventHandler
    public void handle(MaintenancePremiumCalculatedEvent event) {
        log.info("Handling MaintenancePremiumCalculatedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_PREMIUM_CALCULATED, event.maintenanceId().id(), event);
    }

    @EventHandler
    public void handle(MaintenanceExecutedEvent event) {
        log.info("Handling MaintenanceExecutedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_EXECUTED, event.maintenanceId().id(), event);
    }
}
