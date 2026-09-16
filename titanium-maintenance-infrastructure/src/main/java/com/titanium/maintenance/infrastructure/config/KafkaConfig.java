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
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.maintenance.common.constant.MaintenanceConstants;

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
     * 事件序列化用 ObjectMapper：注册 JavaTimeModule，确保 LocalDateTime 等 JSR-310 类型可序列化。
     * 事件（如 MaintenanceExecutedEvent）携带 LocalDateTime 字段，裸 ObjectMapper 会抛
     * InvalidDefinitionException 导致发布失败。
     */
    private ObjectMapper eventObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
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
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
