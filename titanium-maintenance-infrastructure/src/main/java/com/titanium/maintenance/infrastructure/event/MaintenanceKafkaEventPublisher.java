package com.titanium.maintenance.infrastructure.event;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.titanium.common.kafka.KafkaPublishSupport;
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
 * <p>
 * <b>处理组形态</b>（m6-909）：{@link #PROCESSING_GROUP} 以 {@code mode: tracking} +
 * {@code dlq.enabled: true} 装配（见 application.yml）。tracking 是死信队列的前提；DLQ 让发布失败
 * 的事件由 {@code DeadLetterQueueService} 定时重投而非静默丢失——而「失败」的前提是本类的
 * {@code handle(...)} 把异常抛出，见 {@link #publish}。
 * </p>
 * <p>
 * 🔴 <b>线格式</b>：本域载荷是<b>事件 POJO 本身</b>，由生产者 value serializer（注册了
 * {@code JavaTimeModule} 的 {@link org.springframework.kafka.support.serializer.JsonSerializer}）
 * 序列化。改造时<b>不得</b>改为先经 fastjson2 序列化成字符串再发（那样 value serializer 会再编码一次，
 * 与 policy/customer/claim/clause 的「String 逐字节透传」形态不同，改即变线格式）。
 * </p>
 */
@Component
@Slf4j
@ProcessingGroup(MaintenanceKafkaEventPublisher.PROCESSING_GROUP)
public class MaintenanceKafkaEventPublisher {

    /**
     * 跨域出站处理组名。
     * <p>🔴 必须与 application.yml 的 {@code axon.eventhandling.processors.<name>} 键、
     * {@code titanium.axon.outbound-relay.groups} 取值三者一致：漂移时 Axon 会为本组退回默认处理器、
     * 丢失 DLQ 与首启位点配置，处理器仍存在（故不报错）但失去重投能力。</p>
     */
    public static final String PROCESSING_GROUP = "maintenance-kafka-group";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MaintenanceKafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @EventHandler
    public void handle(MaintenanceCreatedEvent event) {
        log.info("Handling MaintenanceCreatedEvent: {}", event);
        publish(MaintenanceConstants.KafkaTopic.MAINTENANCE_CREATED, event.maintenanceId().id(), event);
    }

    @EventHandler
    public void handle(MaintenanceStatusChangedEvent event) {
        log.info("Handling MaintenanceStatusChangedEvent: {}", event);
        publish(MaintenanceConstants.KafkaTopic.MAINTENANCE_STATUS_CHANGED, event.maintenanceId().id(), event);
    }

    @EventHandler
    public void handle(MaintenanceChangeAddedEvent event) {
        log.info("Handling MaintenanceChangeAddedEvent: {}", event);
        publish(MaintenanceConstants.KafkaTopic.MAINTENANCE_CHANGE_ADDED, event.maintenanceId().id(), event);
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
        publish(MaintenanceConstants.KafkaTopic.MAINTENANCE_PREMIUM_CALCULATED, event.maintenanceId().id(), event);
    }

    @EventHandler
    public void handle(MaintenanceExecutedEvent event) {
        log.info("Handling MaintenanceExecutedEvent: {}", event);
        publish(MaintenanceConstants.KafkaTopic.MAINTENANCE_EXECUTED, executionPartitionKey(event), event);
    }

    /**
     * 发布并等待 broker 确认。
     * <p>
     * 🔴 <b>失败会抛出</b>（broker 不可达、确认超时、主题无权限、序列化失败）→ 抛
     * {@code KafkaPublishException}，这是「失败可见 → 入 DLQ → 定时重投」链路的触发点。原先发后即弃
     * future 的写法会让失败静默丢失：既不重试、也不留痕、也无从对账——而 {@code maintenance-executed}
     * 是驱动 policy 域回写保单状态的唯一通道，丢失即保单要素永不更新。
     * </p>
     *
     * @param topic Kafka 主题
     * @param key   分区键（见 {@link #executionPartitionKey}）
     * @param event 事件载荷（POJO，由 value serializer 序列化）
     */
    private void publish(String topic, String key, Object event) {
        KafkaPublishSupport.awaitSent(() -> kafkaTemplate.send(topic, key, event), topic, key);
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
