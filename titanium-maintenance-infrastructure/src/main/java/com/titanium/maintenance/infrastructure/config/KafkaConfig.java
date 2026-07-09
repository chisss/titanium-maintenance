package com.titanium.maintenance.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

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
                .replicas(2)
                .build();
    }

    @Bean
    public NewTopic maintenanceStatusChangedTopic() {
        return TopicBuilder.name(MaintenanceConstants.KafkaTopic.MAINTENANCE_STATUS_CHANGED)
                .partitions(3)
                .replicas(2)
                .build();
    }

    // 生产者配置
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 显式以注册了 JavaTimeModule 的 ObjectMapper 构造 JsonSerializer，避免 LocalDateTime 序列化失败
        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(eventObjectMapper());
        return new DefaultKafkaProducerFactory<>(configProps, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
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
