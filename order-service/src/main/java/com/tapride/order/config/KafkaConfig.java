package com.tapride.order.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        // Kafka instantiates a class-configured serializer via reflection,
        // bypassing Spring Boot's autoconfigured ObjectMapper entirely - that
        // default instance lacks JavaTimeModule and writes java.time.Instant
        // as a raw numeric epoch timestamp instead of an ISO-8601 string,
        // which then confuses every downstream JSON consumer. Constructing
        // the serializer explicitly with a properly configured ObjectMapper
        // fixes this at the source.
        ObjectMapper kafkaObjectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new JsonSerializer<>(kafkaObjectMapper));
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        // This is what actually stitches a single Jaeger trace across all 3
        // services, even though they only ever talk via Kafka messages, never
        // direct HTTP calls to each other. Without this, each service's work
        // would show up as a separate, disconnected trace in Jaeger - you'd see
        // "order-service did something" and "payment-service did something"
        // with no visible link between them, defeating the point of tracing.
        template.setObservationEnabled(true);
        return template;
    }

    // Consumers deliberately deserialize to raw String and parse JSON manually
    // (see SagaEventListener) rather than binding to a shared Java type, since
    // order-service consumes event shapes owned by OTHER services (payment,
    // matching). Binding to a shared DTO would create tight coupling across
    // service boundaries - each service's event schema should evolve independently.
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
       ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // The consumer-side half of the same fix - extracts the trace context
        // that setObservationEnabled(true) on the producer embedded into the
        // Kafka message headers, and continues the same trace here instead of
        // starting a fresh, disconnected one.
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }
}
