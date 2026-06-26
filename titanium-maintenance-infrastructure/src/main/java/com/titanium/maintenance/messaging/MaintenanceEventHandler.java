package com.titanium.maintenance.messaging;

import com.titanium.maintenance.constant.MaintenanceConstants;
import com.titanium.maintenance.event.MaintenanceChangeAddedEvent;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceExecutedEvent;
import com.titanium.maintenance.event.MaintenancePremiumCalculatedEvent;
import com.titanium.maintenance.event.MaintenanceStatusChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MaintenanceEventHandler {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MaintenanceEventHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @EventHandler
    public void handle(MaintenanceCreatedEvent event) {
        log.info("Handling MaintenanceCreatedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_CREATED, event.getMaintenanceId().getId(), event);
    }

    @EventHandler
    public void handle(MaintenanceStatusChangedEvent event) {
        log.info("Handling MaintenanceStatusChangedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_STATUS_CHANGED, event.getMaintenanceId().getId(), event);
    }

    @EventHandler
    public void handle(MaintenanceChangeAddedEvent event) {
        log.info("Handling MaintenanceChangeAddedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_CHANGE_ADDED, event.getMaintenanceId().getId(), event);
    }

    @EventHandler
    public void handle(MaintenancePremiumCalculatedEvent event) {
        log.info("Handling MaintenancePremiumCalculatedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_PREMIUM_CALCULATED, event.getMaintenanceId().getId(), event);
    }

    @EventHandler
    public void handle(MaintenanceExecutedEvent event) {
        log.info("Handling MaintenanceExecutedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_EXECUTED, event.getMaintenanceId().getId(), event);
    }
}