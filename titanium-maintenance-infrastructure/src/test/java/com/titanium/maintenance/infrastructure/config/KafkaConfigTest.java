package com.titanium.maintenance.infrastructure.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.maintenance.event.MaintenanceExecutedEvent;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.endorsement.EndorsementDocument;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * 保全域 Kafka 外发线格式测试（g05r）
 * <p>
 * 🔴 <b>核心回归目标</b>：本域外发消息的时间字段必须是 <b>ISO-8601 文本</b>。
 * {@code KafkaConfig#eventObjectMapper} 曾只注册 {@code JavaTimeModule} 而未禁用
 * {@code WRITE_DATES_AS_TIMESTAMPS}，而该开关 Jackson <b>默认开启</b>，会把 {@code LocalDateTime}
 * 写成整数数组 {@code [2026,9,28,13,52,9]}；两个跨域消费方（document
 * {@code MaintenanceEndorsementIssuedMessage}、policy {@code MaintenanceExecutedMessage}）均以
 * <b>fastjson2</b> 把时间字段读成 {@code LocalDateTime}，fastjson2 只认字符串，遇数组即抛
 * {@code JSONException: string length only support string input}。
 * </p>
 * <p>
 * <b>判据取自「生产端实际装配的 serializer」</b>：经 {@code producerFactory().getValueSerializer()}
 * 反查，而非直调 {@code eventObjectMapper()} —— 锁定的是「生产者真正会发出的字节」，
 * 即便将来替换 mapper 的构造方式，只要装配点未变，判据依然有效。
 * </p>
 * <p>
 * <b>四例互补</b>：①② 以 fastjson2 复现两个消费方的真实解析动作（端到端性质，不写死形态字面量）；
 * ③ 从字节直读文本并以 {@code LocalDateTime.parse} 还原（形态判据，不依赖消费端实现）；
 * ④ 对照组复现缺陷态，<b>证明前三条判据确有区分力</b> —— 若对照组不再报错，说明判据已退化为恒绿，
 * 必须重设计而非放任。
 * </p>
 * <p>
 * 纯对象构造，不启动容器、不连 broker。
 * </p>
 */
class KafkaConfigTest {

    private static final String TOPIC = "maintenance-endorsement-issued";
    private static final LocalDateTime EFFECTIVE_TIME = LocalDateTime.of(2026, 9, 28, 13, 52, 9);
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 9, 28, 13, 52, 30);
    private static final String TENANT_ID = "TENANT-A";

    /**
     * 生产端实际装配的 value serializer —— 生产者真正发出的字节由它决定。
     * <p>
     * 断言锚在此处而非 {@code eventObjectMapper()} 上，是为了让判据落在「装配结果」这一可见契约上。
     * </p>
     * <p>
     * 反射补 {@code bootstrapServers}：{@code producerFactory()} 会把它放进配置 Map，而
     * {@code DefaultKafkaProducerFactory} 内部以 {@code ConcurrentHashMap} 拷贝配置，
     * <b>不接受 null 值</b>（裸 new 出的 {@code KafkaConfig} 该字段为空）。仅为构造 serializer，
     * 不建立任何连接。
     * </p>
     */
    private static JsonSerializer<Object> productionValueSerializer() throws Exception {
        KafkaConfig config = new KafkaConfig();
        Field bootstrapServers = KafkaConfig.class.getDeclaredField("bootstrapServers");
        bootstrapServers.setAccessible(true);
        bootstrapServers.set(config, "localhost:9092");

        DefaultKafkaProducerFactory<String, Object> factory =
                (DefaultKafkaProducerFactory<String, Object>) config.producerFactory();
        return (JsonSerializer<Object>) factory.getValueSerializer();
    }

    /** 以生产端真实 serializer 序列化载荷，返回将写入 Kafka 的报文字符串 */
    private static String serialize(Object payload) throws Exception {
        return new String(productionValueSerializer().serialize(TOPIC, payload), UTF_8);
    }

    /** 批单要素（document 域消费的真实载荷形态） */
    private static EndorsementDocument endorsementDocument() {
        return new EndorsementDocument("MNT-20260928-0001", "POL-20260928-0001", "END-20260928-0001",
                MaintenanceType.POLICY_HOLDER_CHANGE.getCode(), List.of(), EFFECTIVE_TIME, COMPLETED_AT, TENANT_ID);
    }

    @Test
    @DisplayName("① 批单要素外发后，document 域可把 effectiveTime / completedAt 读成 LocalDateTime")
    void shouldLetDocumentSideParseEndorsementTimestamps() throws Exception {
        String json = serialize(endorsementDocument());
        JSONObject payload = JSONObject.parseObject(json);

        LocalDateTime parsedEffective = assertDoesNotThrow(
                () -> payload.getObject("effectiveTime", LocalDateTime.class),
                () -> "document 域以 fastjson2 读取 effectiveTime 失败，实际报文: " + json);
        LocalDateTime parsedCompleted = assertDoesNotThrow(
                () -> payload.getObject("completedAt", LocalDateTime.class),
                () -> "document 域以 fastjson2 读取 completedAt 失败，实际报文: " + json);

        assertEquals(EFFECTIVE_TIME, parsedEffective, "生效时间跨域往返后必须等值");
        assertEquals(COMPLETED_AT, parsedCompleted, "完成时间跨域往返后必须等值");
    }

    @Test
    @DisplayName("② 保全执行事件外发后，policy 域可把 effectiveTime 读成 LocalDateTime")
    void shouldLetPolicySideParseMaintenanceExecutedTimestamp() throws Exception {
        String json = serialize(new MaintenanceExecutedEvent(MaintenanceId.of("MNT-20260928-0001"),
                "POL-20260928-0001", MaintenanceType.POLICY_HOLDER_CHANGE, EFFECTIVE_TIME, "批改生效",
                COMPLETED_AT, "op-20260928", TENANT_ID));
        JSONObject payload = JSONObject.parseObject(json);

        LocalDateTime parsed = assertDoesNotThrow(
                () -> payload.getObject("effectiveTime", LocalDateTime.class),
                () -> "policy 域以 fastjson2 读取 effectiveTime 失败，实际报文: " + json);

        assertEquals(EFFECTIVE_TIME, parsed, "生效时间跨域往返后必须等值");
    }

    @Test
    @DisplayName("③ 时间字段必须是标准 ISO-8601 文本：可被 LocalDateTime.parse 还原")
    void shouldEmitIsoTextRestorableByLocalDateTimeParse() throws Exception {
        String json = serialize(endorsementDocument());

        // 数组形态下 fastjson2 的 getString 同样直接抛异常，故本步既是取值也是形态判据
        String rawEffective = assertDoesNotThrow(() -> JSONObject.parseObject(json).getString("effectiveTime"),
                () -> "时间字段不得为数组等非文本形态，实际报文: " + json);
        assertNotNull(rawEffective, () -> "effectiveTime 必须出现在报文中，实际报文: " + json);

        // 期望值锚在 EFFECTIVE_TIME 本身（而非另写一份字面量），避免内联副本随权威源漂移
        assertEquals(EFFECTIVE_TIME, LocalDateTime.parse(rawEffective),
                () -> "时间字段必须为标准 ISO-8601 文本，实际值: " + rawEffective);
    }

    @Test
    @DisplayName("④ 对照组：Jackson 库级默认下，同一解析动作必然失败（证明上述判据有区分力）")
    void jacksonLibraryDefaultMustBreakTheSameAssertion() throws Exception {
        // 🔴 此处复述的是 **Jackson 的库级默认行为**（仅注册 JavaTimeModule、不关时间戳数组），
        //    不是本域实现的副本 —— 它不随本域代码演进漂移，故可作常驻对照。
        ObjectMapper jacksonDefault = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = new String(new JsonSerializer<>(jacksonDefault).serialize(TOPIC, endorsementDocument()), UTF_8);

        // 🔴 不锁死异常类型：本判据断的是「失败」这一性质，而非「以何种异常失败」。实测同一缺陷在
        //    fastjson2 的不同入口抛不同类型——字段级 getObject 抛 UnsupportedOperationException，
        //    而 DLT 留痕的 record 绑定路径（parseObject(payload, 类)）抛 JSONException。
        //    锁死类型会在 fastjson2 升级时变成假红，反而掩盖真正的失守。
        assertThrows(RuntimeException.class,
                () -> JSONObject.parseObject(json).getObject("effectiveTime", LocalDateTime.class),
                () -> "对照组未按预期失败 ⇒ 前三例已失去区分力（恒绿），必须重设计判据。实际报文: " + json);
    }
}
