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

    /**
     * 发布保全保费试算事件。
     * <p>
     * 🔴 本主题仓内**无消费方，且经判定不应有**（2026-09-14 m6-904）：{@code totalAmount}/{@code refundAmount}
     * 是人工试算值，会被同聚合的结构化差额覆盖；客户补收/退还的权威通道是 maintenance → billing 的 Feign
     * 差额记账（{@code BillingPremiumLifecyclePort.post}）。据本事件在 billing 侧开单会与它重复计量同一笔
     * 补收。判定依据与未来接线前置条件见 {@code docs/技术文档/跨域事件目录-2026-09.md} §六.12。
     * </p>
     */
    @EventHandler
    public void handle(MaintenancePremiumCalculatedEvent event) {
        log.info("Handling MaintenancePremiumCalculatedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_PREMIUM_CALCULATED, event.maintenanceId().id(), event);
    }

    @EventHandler
    public void handle(MaintenanceExecutedEvent event) {
        log.info("Handling MaintenanceExecutedEvent: {}", event);
        kafkaTemplate.send(MaintenanceConstants.KafkaTopic.MAINTENANCE_EXECUTED, executionPartitionKey(event), event);
    }

    /**
     * 保全执行事件的分区键取**保单ID**，而非本聚合标识保全ID。
     * <p>
     * 🔴 分区键的判据是「下游消费的保序维度」，不是「事件的产生者」。本主题的唯一消费方是 policy 域
     * {@code MaintenanceExecutedEventListener}，它按 {@code message.policyId()} 回写保单状态与要素。
     * 若按 maintenanceId 分区，同一保单的多次保全执行会散落不同分区、被并行消费，policy 侧「字段级
     * version + 快照哈希」的过期写保护随之失效（旧版本回写可能后到并覆盖新版本）。故必须按保单分区。
     * </p>
     * <p>
     * 其余四个主题（created/status-changed/change-added/premium-calculated）全仓无消费方，其分区键
     * 保持按保全ID（见各 {@code handle} 方法），待出现消费方时再按同样的判据评估。
     * {@code premium-calculated} 是例外：该主题**已评估并判定不应有仓内消费方**（见其 {@code handle} 方法的
     * 说明与跨域事件目录 §六.12），分区键随之**确定**保持保全ID——即便未来接线，消费端写单元也是保全案件
     * 派生的账单，而非保单聚合，故仍按保全ID分区。
     * </p>
     */
    private String executionPartitionKey(MaintenanceExecutedEvent event) {
        if (event.policyId() != null) {
            return event.policyId();
        }
        // 理论不可达：CreateMaintenanceCommand 强制 policyId 非空（Maintenance#on(MaintenanceCreatedEvent) 回放填充），
        // 仅旧事件流可能缺字段。此时无法按保单分区，退化为按保全ID分区——各保全案件天然独立，单案件内仍保序——
        // 并告警暴露数据异常。
        log.warn("保全执行事件缺少 policyId，退化按 maintenanceId 分区，同一保单的多次保全回写无法保证有序: "
                + "maintenanceId={}", event.maintenanceId().id());
        return event.maintenanceId().id();
    }
}
