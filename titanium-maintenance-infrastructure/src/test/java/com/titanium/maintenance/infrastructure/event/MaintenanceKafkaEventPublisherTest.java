package com.titanium.maintenance.infrastructure.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.axonframework.config.ProcessingGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.titanium.common.kafka.KafkaPublishException;
import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.maintenance.event.MaintenanceCreatedEvent;
import com.titanium.maintenance.event.MaintenanceExecutedEvent;
import com.titanium.maintenance.event.MaintenancePremiumCalculatedEvent;
import com.titanium.maintenance.event.MaintenanceStatusChangedEvent;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * 保全域 Kafka 事件发布器测试
 * <p>
 * 核心回归目标（m0-718）：{@code maintenance-executed} 原按保全ID（maintenanceId）分区，而其唯一消费方
 * policy 域按 {@code message.policyId()} 回写保单——同一保单的多次保全会散落不同分区被并行消费，
 * policy 侧「字段级 version + 快照哈希」的过期写保护随之失效。本测试锁死「执行事件必须按保单ID分区」，
 * 并防止顺手把其余无消费方主题的分区语义一并改掉。
 * </p>
 * <p>
 * 另锁死出站可靠性两件事（m6-909）：④ 🔴 <b>发布失败必须抛出</b>——出站处理组为 tracking + DLQ 形态，
 * Axon 只对「处理器抛出的异常」入队死信；失败仅记日志则事件既不重投也不留痕，而
 * {@code maintenance-executed} 是 policy 域回写保单状态的唯一通道，丢失即保单永不更新；⑤ 出站组名与
 * {@code @ProcessingGroup} 一致，漂移会使 DLQ 与首启位点配置同时落空。
 * </p>
 * 纯 mockito，不启动容器。
 */
class MaintenanceKafkaEventPublisherTest {

    private static final String MAINTENANCE_ID = "MNT-20260911-0001";
    private static final String POLICY_ID = "POL-20260911-0001";
    private static final String TENANT_ID = "TENANT-A";

    private KafkaTemplate<String, Object> kafkaTemplate;
    private MaintenanceKafkaEventPublisher publisher;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        // 默认发布成功：发布器现在会等待 broker 确认，未 stub 时 send 返回 null 会直接 NPE
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        publisher = new MaintenanceKafkaEventPublisher(kafkaTemplate);
    }

    private MaintenanceExecutedEvent executedEvent(String policyId) {
        return new MaintenanceExecutedEvent(MaintenanceId.of(MAINTENANCE_ID), policyId, MaintenanceType.POLICY_SUSPENSION,
                LocalDateTime.of(2026, 9, 11, 10, 0), "保全执行完成", LocalDateTime.of(2026, 9, 11, 10, 5), "operator",
                TENANT_ID);
    }

    @Test
    @DisplayName("【核心】执行事件按保单ID分区：与 policy 消费端的回写维度一致")
    void shouldPartitionExecutedEventByPolicyId() {
        MaintenanceExecutedEvent event = executedEvent(POLICY_ID);

        publisher.handle(event);

        verify(kafkaTemplate).send(eq(MaintenanceConstants.KafkaTopic.MAINTENANCE_EXECUTED), eq(POLICY_ID), eq(event));
    }

    @Test
    @DisplayName("保单ID缺失：退化为按保全ID分区并告警，不抛异常")
    void shouldFallBackToMaintenanceIdWhenPolicyIdMissing() {
        MaintenanceExecutedEvent event = executedEvent(null);

        assertDoesNotThrow(() -> publisher.handle(event));

        verify(kafkaTemplate).send(eq(MaintenanceConstants.KafkaTopic.MAINTENANCE_EXECUTED), eq(MAINTENANCE_ID),
                eq(event));
    }

    @Test
    @DisplayName("无消费方的其余主题仍按保全ID分区：分区键语义不被顺手改动")
    void shouldKeepMaintenanceIdPartitionForUnconsumedTopics() {
        MaintenanceCreatedEvent created = new MaintenanceCreatedEvent(MaintenanceId.of(MAINTENANCE_ID), null, null, null,
                null, null, "描述", LocalDateTime.of(2026, 9, 11, 9, 0), "operator", TENANT_ID);
        MaintenanceStatusChangedEvent statusChanged = new MaintenanceStatusChangedEvent(MaintenanceId.of(MAINTENANCE_ID),
                null, null, "审批通过", LocalDateTime.of(2026, 9, 11, 9, 30), "operator", TENANT_ID);
        MaintenancePremiumCalculatedEvent premiumCalculated = new MaintenancePremiumCalculatedEvent(
                MaintenanceId.of(MAINTENANCE_ID), new BigDecimal("120.00"), BigDecimal.ZERO, "计算明细",
                LocalDateTime.of(2026, 9, 11, 9, 45), "operator", TENANT_ID);

        publisher.handle(created);
        publisher.handle(statusChanged);
        publisher.handle(premiumCalculated);

        verify(kafkaTemplate).send(eq(MaintenanceConstants.KafkaTopic.MAINTENANCE_CREATED), eq(MAINTENANCE_ID),
                eq(created));
        verify(kafkaTemplate).send(eq(MaintenanceConstants.KafkaTopic.MAINTENANCE_STATUS_CHANGED), eq(MAINTENANCE_ID),
                eq(statusChanged));
        verify(kafkaTemplate).send(eq(MaintenanceConstants.KafkaTopic.MAINTENANCE_PREMIUM_CALCULATED),
                eq(MAINTENANCE_ID), eq(premiumCalculated));
    }

    @Test
    @DisplayName("🔴 发布失败必须抛出（不再静默丢弃），否则事件不会进死信队列、无法重投")
    void shouldThrowWhenSendFails() {
        CompletableFuture<SendResult<String, Object>> failed = CompletableFuture
                .failedFuture(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        MaintenanceExecutedEvent event = executedEvent(POLICY_ID);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class, () -> publisher.handle(event));

        assertTrue(exception.getMessage().contains(MaintenanceConstants.KafkaTopic.MAINTENANCE_EXECUTED),
                "异常须携带主题以便定位");
        assertNotNull(exception.getCause(), "须保留底层失败原因");
    }

    @Test
    @DisplayName("出站处理组名与 @ProcessingGroup 一致（漂移会使 DLQ 与首启位点配置同时落空）")
    void shouldDeclareProcessingGroupConsistently() {
        ProcessingGroup annotation = MaintenanceKafkaEventPublisher.class.getAnnotation(ProcessingGroup.class);

        assertNotNull(annotation, "出站发布器必须声明 @ProcessingGroup");
        assertEquals(MaintenanceKafkaEventPublisher.PROCESSING_GROUP, annotation.value());
        assertEquals("maintenance-kafka-group", MaintenanceKafkaEventPublisher.PROCESSING_GROUP,
                "组名须与 application.yml 的 axon.eventhandling.processors 键及 outbound-relay.groups 一致");
    }
}
