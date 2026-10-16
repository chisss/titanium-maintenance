package com.titanium.maintenance.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.common.json.JacksonMappers;
import com.titanium.common.messaging.DltMetricsRecoverer;
import com.titanium.maintenance.common.constant.MaintenanceConstants;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableKafka
public class KafkaConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * 主题副本因子：**部署环境属性**，不是代码常量。
     * <p>🔴 D-501-27：原硬编码 {@code replicas(2)}，单 broker 环境下 {@code KafkaAdmin} 创建主题必然失败
     * （{@code InvalidReplicationFactorException}）。现默认 1（单 broker 开箱可用），
     * 多 broker 环境经配置覆盖为 3。</p>
     */
    @Value("${kafka.topic.replication-factor:1}")
    private int topicReplicationFactor;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * 事件序列化用 ObjectMapper：注册 JavaTimeModule 且**固定时间形态为 ISO-8601 字符串**。
     * <p>
     * 🔴 <b>「能不能序列化」与「序列化成什么」是两件事</b>：注册 {@code JavaTimeModule} 只解决前者；
     * 形态由 {@code WRITE_DATES_AS_TIMESTAMPS} 决定，Jackson <b>默认 true</b>，会把 {@code LocalDateTime}
     * 写成整数数组 {@code [2026,9,28,13,52,9]}。本域两个跨域消费方——
     * document {@code MaintenanceEndorsementIssuedMessage}、policy {@code MaintenanceExecutedMessage}——
     * 均以 <b>fastjson2</b> 把时间字段读成 {@code LocalDateTime}，而 fastjson2 只认字符串，
     * 遇数组即抛 {@code JSONException: string length only support string input}。
     * </p>
     * <p>
     * 🔴 <b>该默认值曾令两条链路自建成起从未成功</b>（2026-09-30 g05r 修复）：
     * {@code maintenance-endorsement-issued} 上全部真实消息消费失败（document 侧 catch-all 静默吞掉
     * ⇒ 批单单证零产出、监控无任何信号）；{@code maintenance-executed} 部分失败进 DLT
     * （policy 侧解析在 try 之外 ⇒ 异常逃逸，但位点照常提交、LAG 恒为 0）。
     * </p>
     * <p>
     * ISO 字符串是跨语言/跨序列化库的通用形态，故修在此处（生产端一处）而非各消费方。
     * 回归守护见 {@code KafkaConfigTest}。
     * </p>
     */
    // ✅ m22-03（审查报告 SPR-04）定性：本处**有意独立构造**——Kafka 事件外发需要**确定的序列化配置**（显式注册 JavaTimeModule + 固定 ISO 时间形态），
    // 复用 Spring 全局实例会把消息格式绑到 Web 层的配置演进上，反而不稳。
    // ✅ m26-06（AXN-05）续：独立构造保留，但**构造点收口到统一工厂** JacksonMappers.crossDomainWireFormat()
    // ——「JSR-310 感知 + ISO 时间串」这套跨域线格式由工厂的具名方法承载，不再各域手抄一份、各自漂移。
    private ObjectMapper eventObjectMapper() {
        // 🔴 跨域线格式契约（ISO 时间串）由 JacksonMappers.crossDomainWireFormat() 承载，
        // 该工厂方法内的「禁用时间戳」一旦移除，即令 document / policy 两条消费链路再次全量失败
        return JacksonMappers.crossDomainWireFormat();
    }

    // 创建Kafka主题
    @Bean
    public NewTopic maintenanceCreatedTopic() {
        return TopicBuilder.name(MaintenanceConstants.KafkaTopic.MAINTENANCE_CREATED)
                .partitions(3)
                .replicas(topicReplicationFactor)
                .build();
    }

    @Bean
    public NewTopic maintenanceStatusChangedTopic() {
        return TopicBuilder.name(MaintenanceConstants.KafkaTopic.MAINTENANCE_STATUS_CHANGED)
                .partitions(3)
                .replicas(topicReplicationFactor)
                .build();
    }

    /**
     * 生产者工厂：key 用 String、value 用注册了 JavaTimeModule 的 JSON 序列化。
     * <p>
     * 🔴 <b>可靠性参数必须在此显式声明</b>：本域 bootstrap 虽写了 {@code spring.kafka.producer.*}，
     * 但全仓未引入 {@code spring-boot-kafka}（{@code KafkaProperties} 所在模块），Boot 的 Kafka
     * 自动配置不激活，yml 里的生产者配置<b>没有任何消费者</b>——写了也不生效，读配置的人却会以为已配好。
     * 故生产者参数以本方法为唯一事实来源。
     * </p>
     * <ul>
     *   <li>{@code acks=all}：leader 需等全部同步副本确认，防 leader 切换时丢消息（Kafka 默认
     *       {@code acks=1} 只等 leader 本地写入，leader 随即崩溃即丢）；</li>
     *   <li>{@code enable.idempotence=true}：生产者幂等，重试不会产生重复消息（配合 acks=all 才可开启）；</li>
     *   <li>{@code retries}：瞬时故障（网络抖动、leader 选举）自动重试，避免直接落入死信队列。</li>
     * </ul>
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        // 显式以注册了 JavaTimeModule 的 ObjectMapper 构造 JsonSerializer，避免 LocalDateTime 序列化失败
        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(eventObjectMapper());
        return new DefaultKafkaProducerFactory<>(configProps, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Kafka Admin：消费本类声明的 {@link NewTopic} Bean 并由其在 broker 上创建主题。
     * <p>🔴 D-501-27：本域依赖裸 {@code spring-kafka}，Boot 的 Kafka 自动配置不激活，
     * {@code KafkaAdmin} 从未被注册 —— 下方两个 {@code NewTopic} 声明<b>静默失效</b>，
     * broker 上的 {@code maintenance-created} / {@code maintenance-status-changed} 实为生产者
     * 首次发送时 auto-create 所建。实测佐证：声明 {@code partitions(3).replicas(2)}，
     * broker 上实为 <b>1 分区 1 副本</b>。对齐 billing / claim / payment / regulatory 同名样板。</p>
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    // 消费者配置
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            MeterRegistry meterRegistry) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // 🔴 必须显式挂载：本仓未引入 spring-boot-kafka，Boot 不会把 CommonErrorHandler 自动接到
        //    容器工厂上；不挂载即退回 Spring Kafka 默认（10 次零间隔重试后丢弃），重试耗尽的消息静默丢失。
        factory.setCommonErrorHandler(kafkaDefaultErrorHandler(meterRegistry));
        return factory;
    }

    /**
     * 入站消息错误处理器（m20-11 / 审查报告 MSG-01）。
     * <p>
     * 重试 3 次（间隔 2s）后由 {@link DeadLetterPublishingRecoverer} 把记录转发到
     * {@code {原主题}-dlt}，不再静默丢弃。死信主题在 Broker 端按需创建
     * （生产环境应显式建主题并配监控告警——见 policy 域的 INBOUND_TOPICS + KafkaAdmin 加强版）。
     * </p>
     * <p>
     * 死信投递经 {@link DltMetricsRecoverer} 旁路计数（{@code titanium.kafka.dlt.published} /
     * {@code titanium.kafka.dlt.recovery.failed}），死信条数与投递失败可被 Prometheus 采集告警（G16/AC-03）。
     * </p>
     */
    @Bean
    public DefaultErrorHandler kafkaDefaultErrorHandler(MeterRegistry meterRegistry) {
        return new DefaultErrorHandler(
                new DltMetricsRecoverer(new DeadLetterPublishingRecoverer(kafkaTemplate()), meterRegistry),
                new FixedBackOff(2000L, 3L));
    }
}
