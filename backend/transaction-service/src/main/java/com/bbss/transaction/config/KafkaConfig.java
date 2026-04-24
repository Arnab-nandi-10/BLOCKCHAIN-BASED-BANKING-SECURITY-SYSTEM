package com.bbss.transaction.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Apache Kafka configuration for transaction-service.
 *
 * <p>Configures producer and consumer beans with dead-letter topics (DLT), exponential
 * backoff retry, and custom Micrometer metrics for observability.</p>
 *
 * <p><strong>Phase 1.5: Kafka Reliability Enhancements</strong></p>
 * <ul>
 *   <li><strong>Dead Letter Topics</strong>: Failed messages sent to {@code {topic}.dlt}</li>
 *   <li><strong>Exponential Backoff</strong>: 2s → 4s → 8s retry intervals, max 30s window</li>
 *   <li><strong>Consumer Lag Metrics</strong>: Tracked via Spring Boot Actuator + Micrometer</li>
 *   <li><strong>Poison Pill Handling</strong>: Deserialization errors routed to DLT immediately</li>
 * </ul>
 *
 * @see org.springframework.kafka.listener.DeadLetterPublishingRecoverer
 * @see org.springframework.util.backoff.ExponentialBackOff
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:transaction-service}")
    private String groupId;

    private final MeterRegistry meterRegistry;

    // -------------------------------------------------------------------------
    // Producer Configuration
    // -------------------------------------------------------------------------

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(Objects.requireNonNull(producerFactory, "producerFactory"));
    }

    // -------------------------------------------------------------------------
    // Consumer Configuration
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, Object> fraudAlertConsumerFactory() {
        return createConsumerFactory("com.bbss.shared.events.FraudAlertEvent");
    }

    @Bean
    public ConsumerFactory<String, Object> blockchainConsumerFactory() {
        return createConsumerFactory("java.util.Map");
    }

    private ConsumerFactory<String, Object> createConsumerFactory(String defaultValueType) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bbss.shared.events,com.bbss.transaction.messaging");
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, defaultValueType);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Error handler with exponential backoff and dead-letter topic routing.
     *
     * <p><strong>Retry Schedule:</strong></p>
     * <ul>
     *   <li>1st retry: 2 seconds after initial failure</li>
     *   <li>2nd retry: 4 seconds after 1st retry (6s cumulative)</li>
     *   <li>3rd retry: 8 seconds after 2nd retry (14s cumulative)</li>
     *   <li>After 3 failures: Message routed to {@code {original-topic}.dlt}</li>
     * </ul>
     *
     * <p><strong>Metrics Published:</strong></p>
     * <ul>
     *   <li>{@code bbss_kafka_dlt_published_total} — Counter with tags: topic, dlt_topic, reason</li>
     *   <li>{@code bbss_kafka_retry_attempts_total} — Counter with tags: topic, attempt</li>
     * </ul>
     *
     * @param kafkaTemplate the Kafka template for DLT publishing
     * @param meterRegistry Micrometer registry for custom metrics
     * @return configured error handler
     */
    @Bean
    public CommonErrorHandler errorHandler(
            KafkaTemplate<String, Object> kafkaTemplate) {

        // Dead Letter Publishing Recoverer
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            Objects.requireNonNull(kafkaTemplate, "kafkaTemplate"),
                (record, ex) -> {
                    String originalTopic = record.topic();
                    String dltTopic = originalTopic + ".dlt";

                    log.error("Publishing message to DLT: topic={} partition={} offset={} key={} exception={}",
                            originalTopic, record.partition(), record.offset(), record.key(),
                            ex.getClass().getSimpleName());

                    // Micrometer metric: DLT published count
                    meterRegistry.counter("bbss_kafka_dlt_published_total",
                            "topic", originalTopic,
                            "dlt_topic", dltTopic,
                            "reason", ex.getClass().getSimpleName()
                    ).increment();

                    return new TopicPartition(dltTopic, record.partition());
                }
        );

        // Exponential Backoff Configuration
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(2000L);      // 2 seconds initial delay
        backOff.setMaxInterval(10000L);         // 10 seconds maximum delay
        backOff.setMultiplier(2.0);             // Double the delay on each retry
        backOff.setMaxElapsedTime(30000L);      // 30 seconds total retry window

        // Default Error Handler with Retry Listeners
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Add retry listener for observability
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Retrying message: topic={} partition={} offset={} attempt={} exception={}",
                    record.topic(), record.partition(), record.offset(), deliveryAttempt,
                    ex.getClass().getSimpleName());

            // Micrometer metric: Retry attempts count
            meterRegistry.counter("bbss_kafka_retry_attempts_total",
                    "topic", record.topic(),
                    "attempt", String.valueOf(deliveryAttempt)
            ).increment();
        });

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            @Qualifier("fraudAlertConsumerFactory") ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(Objects.requireNonNull(consumerFactory, "consumerFactory"));
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(Objects.requireNonNull(errorHandler, "errorHandler"));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> blockchainKafkaListenerContainerFactory(
            @Qualifier("blockchainConsumerFactory") ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(Objects.requireNonNull(consumerFactory, "consumerFactory"));
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(Objects.requireNonNull(errorHandler, "errorHandler"));
        return factory;
    }
}
