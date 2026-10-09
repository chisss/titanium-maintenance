package com.titanium.maintenance.infrastructure.adapter.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.titanium.common.kafka.KafkaPublishException;
import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.maintenance.valueobject.reissue.PolicyReissueDocument;

/**
 * 保单补发单证请求适配器测试（g04-09，出站侧）
 * <p>
 * 锁死三条契约：① 投递主题为 {@code maintenance-policy-reissue-requested}，与《跨域事件目录-2026-09》
 * §三 登记值逐字一致——主题漂移会使补发请求静默不出，而案件已按「请求事实已离开本域」置为生效；② 🔴 <b>分区键
 * 取保单ID</b>（与批单出口取案件ID 恰好相反），判据是「下游消费的保序维度」：补发指向保单自身凭证的替换关系，
 * 同保单的多次补发（挂失→解除→再挂失）语义上互为先后，散到不同分区被并行消费即等于放弃保序；③ 🔴 <b>发布失败
 * 必须抛出</b>——补发请求事实是本案生效的实质内容，静默丢弃将出现「案件显示已生效、请求却从未离开本域」且无从
 * 对账，故抛出后由调用方记为生效失败。
 * </p>
 * <p>
 * 另断言线格式为要素 POJO 本身（由生产者 value serializer 序列化），<b>不得</b>二次编码为字符串。纯 mockito，
 * 不启动容器。
 * </p>
 */
class PolicyReissueDocumentAdapterTest {

    /** 跨域主题字面量：与跨域事件目录 §三 登记列逐字一致，防常量别名指向漂移 */
    private static final String        REISSUE_TOPIC  = "maintenance-policy-reissue-requested";
    private static final String        MAINTENANCE_ID = "MNT-20260929-0001";
    private static final String        POLICY_ID      = "POL-20260929-0001";
    private static final String        REQUEST_ID     = "RISS0123456789abcdef0123456789ab";
    private static final String        TENANT_ID      = "TENANT-A";
    private static final LocalDateTime NOW            = LocalDateTime.of(2026, 9, 29, 10, 0);

    private KafkaTemplate<String, Object> kafkaTemplate;
    private PolicyReissueDocumentAdapter  adapter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        // 默认发布成功：适配器经 KafkaPublishSupport.awaitSent 等待 broker 确认，未 stub 时 send 返回 null 会直接 NPE
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        adapter = new PolicyReissueDocumentAdapter(kafkaTemplate);
    }

    @Test
    @DisplayName("【核心】投递主题与分区键：主题为补发请求主题，分区键取保单ID（与下游替换关系的保序维度一致）")
    void shouldPublishToReissueTopicPartitionedByPolicyId() {
        PolicyReissueDocument document = reissueDocument(MAINTENANCE_ID, REQUEST_ID);

        String publishedKey = adapter.requestPolicyReissue(document);

        assertEquals(REISSUE_TOPIC, MaintenanceConstants.KafkaTopic.MAINTENANCE_POLICY_REISSUE_REQUESTED,
                "主题常量别名必须指向跨域目录登记的字面值");
        verify(kafkaTemplate).send(eq(REISSUE_TOPIC), eq(POLICY_ID), eq(document));
        assertEquals(REQUEST_ID, publishedKey, "出站结果须回传补发请求号作为幂等键");
    }

    @Test
    @DisplayName("🔴 保序判据：同保单的两次补发（不同案件）必须落到同一分区")
    void shouldPartitionTwoCasesOfSamePolicyTogether() {
        adapter.requestPolicyReissue(reissueDocument("MNT-A", "RISS" + "a".repeat(28)));
        adapter.requestPolicyReissue(reissueDocument("MNT-B", "RISS" + "b".repeat(28)));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(2)).send(anyString(), keys.capture(), any());

        List<String> published = keys.getAllValues();
        assertNotEquals("MNT-A", "MNT-B", "夹具前提：两次出站来自不同案件");
        assertEquals(published.get(0), published.get(1),
                "分区键若取案件ID，同保单的多次补发将并行消费——后到的旧请求可能覆盖新请求");
        assertEquals(POLICY_ID, published.getFirst(), "保序维度是保单，分区键必须为保单ID");
    }

    @Test
    @DisplayName("线格式：投递要素 POJO 本身，不预序列化为字符串（避免 value serializer 二次编码）")
    void shouldPublishElementPojoInsteadOfPreSerializedString() {
        PolicyReissueDocument document = reissueDocument(MAINTENANCE_ID, REQUEST_ID);

        adapter.requestPolicyReissue(document);

        verify(kafkaTemplate).send(anyString(), anyString(), eq(document));
    }

    @Test
    @DisplayName("🔴 发布失败必须抛出（不静默丢弃），否则案件显示已生效而补发请求从未离开本域")
    void shouldThrowWhenSendFails() {
        CompletableFuture<SendResult<String, Object>> failed = CompletableFuture
                .failedFuture(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        PolicyReissueDocument document = reissueDocument(MAINTENANCE_ID, REQUEST_ID);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> adapter.requestPolicyReissue(document));

        assertTrue(exception.getMessage().contains(REISSUE_TOPIC), "异常须携带主题以便定位");
        assertTrue(exception.getMessage().contains(POLICY_ID), "异常须携带分区键，便于按保单复盘");
        assertNotNull(exception.getCause(), "须保留底层失败原因");
    }

    /** 构造补发请求事实（六个要素齐备，覆盖出站载荷） */
    private static PolicyReissueDocument reissueDocument(String maintenanceId, String reissueRequestId) {
        return new PolicyReissueDocument(
                maintenanceId, POLICY_ID, "POLICY_REISSUE", reissueRequestId, NOW, TENANT_ID);
    }
}
