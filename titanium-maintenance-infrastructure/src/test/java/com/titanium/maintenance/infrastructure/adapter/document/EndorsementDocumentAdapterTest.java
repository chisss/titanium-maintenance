package com.titanium.maintenance.infrastructure.adapter.document;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.titanium.common.kafka.KafkaPublishException;
import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.maintenance.valueobject.endorsement.EndorsementDocument;
import com.titanium.maintenance.valueobject.endorsement.EndorsementFieldChange;
import com.titanium.maintenance.valueobject.endorsement.EndorsementItem;

/**
 * 保全批单单证适配器测试（出站侧）
 * <p>
 * 锁死三条契约：① 投递主题为 {@code maintenance-endorsement-issued}，与 document 域入站监听器
 * （{@code MaintenanceEndorsementDocumentEventListener}）的订阅主题、及
 * {@code CrossDomainTopics.MAINTENANCE_ENDORSEMENT_ISSUED} 登记值三方一致——主题漂移会使批单静默不出；
 * ② 🔴 <b>分区键取保全案件ID</b>，判据是「下游消费的保序维度」：document 侧建档单元是「案件派生的批单」，
 * 同一案件的多次出站须保序（同一保单可派生多案，各案彼此独立，故不按保单分区）；
 * ③ 🔴 <b>发布失败必须抛出</b>——保全批单是生效事实的对外凭证，静默丢失将导致案件已生效而批单永久缺失
 * 且无从对账；抛出后由出站处理组重试并最终入 DLQ 重投。
 * </p>
 * <p>
 * 另断言线格式为要素 POJO 本身（由生产者 value serializer 序列化），<b>不得</b>二次编码为字符串。
 * 纯 mockito，不启动容器。
 * </p>
 */
class EndorsementDocumentAdapterTest {

    private static final String        MAINTENANCE_ID = "MNT-20260914-0001";
    private static final String        POLICY_ID      = "POL-20260914-0001";
    private static final String        TENANT_ID      = "TENANT-A";
    private static final LocalDateTime NOW            = LocalDateTime.of(2026, 9, 14, 10, 0);

    private KafkaTemplate<String, Object> kafkaTemplate;
    private EndorsementDocumentAdapter    adapter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        // 默认发布成功：适配器经 KafkaPublishSupport.awaitSent 等待 broker 确认，未 stub 时 send 返回 null 会直接 NPE
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        adapter = new EndorsementDocumentAdapter(kafkaTemplate);
    }

    @Test
    @DisplayName("【核心】投递主题与分区键：主题为批单出具主题，分区键取保全案件ID（与下游建档单元一致）")
    void shouldPublishToEndorsementTopicPartitionedByMaintenanceId() {
        EndorsementDocument document = endorsementDocument();

        String publishedKey = adapter.archiveEndorsementDocument(document);

        verify(kafkaTemplate).send(eq(MaintenanceConstants.KafkaTopic.MAINTENANCE_ENDORSEMENT_ISSUED),
                eq(MAINTENANCE_ID), eq(document));
        assertEquals(MAINTENANCE_ID, publishedKey, "归档结果须回传保全案件号作为出站幂等键");
    }

    @Test
    @DisplayName("线格式：投递要素 POJO 本身，不预序列化为字符串（避免 value serializer 二次编码）")
    void shouldPublishElementPojoInsteadOfPreSerializedString() {
        EndorsementDocument document = endorsementDocument();

        adapter.archiveEndorsementDocument(document);

        verify(kafkaTemplate).send(anyString(), anyString(), eq(document));
    }

    @Test
    @DisplayName("🔴 发布失败必须抛出（不静默丢弃），否则批单不会进死信队列、无法重投")
    void shouldThrowWhenSendFails() {
        CompletableFuture<SendResult<String, Object>> failed = CompletableFuture
                .failedFuture(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        EndorsementDocument document = endorsementDocument();

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> adapter.archiveEndorsementDocument(document));

        assertTrue(exception.getMessage().contains(MaintenanceConstants.KafkaTopic.MAINTENANCE_ENDORSEMENT_ISSUED),
                "异常须携带主题以便定位");
        assertNotNull(exception.getCause(), "须保留底层失败原因");
    }

    /** 构造批单要素（含一个保全项与一条字段变更，覆盖嵌套结构） */
    private static EndorsementDocument endorsementDocument() {
        return new EndorsementDocument(MAINTENANCE_ID, POLICY_ID, "POLICY_INFO_CHANGE",
                List.of(new EndorsementItem("POLICY_INFO_CHANGE", "保单信息变更",
                        List.of(new EndorsementFieldChange("policy-1", "policy.holder.mobile", "13800000000",
                                "13900000000")))),
                NOW, NOW.plusMinutes(2), TENANT_ID);
    }
}
