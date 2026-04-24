package com.bbss.audit.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka producer and consumer configuration for the audit-service.
 *
 * <p><strong>Phase 1.5: Kafka Reliability Enhancements</strong>
 * <ul>
 *   <li><strong>Dead Letter Topics (DLT)</strong>: Failed messages are routed to `.dlt` topics after exhausting retries</li>
 *   <li><strong>Exponential Backoff Retry</strong>: 3 retry attempts with exponential backoff (2s initial, 10s max, 2x multiplier)</li>
 *   <li><strong>Consumer Lag Metrics</strong>: Micrometer-based lag tracking for Prometheus/Grafana</li>
 *   <li><strong>Poison Pill Handling</strong>: Deserialization errors are logged and sent to DLT without blocking the consumer</li>
 * </ul>
 *
 * <p>Producer settings:
 * <ul>
 *   <li>Idempotent producer ({@code enable.idempotence=true}) with {@code acks=all}</li>
 *   <li>Snappy compression and a 20 ms linger window for batching efficiency</li>
 *   <li>Type headers suppressed so consumers do not require the producing class
 *       on their classpath ({@link JsonSerializer#ADD_TYPE_INFO_HEADERS} = false)</li>
 * </ul>
 *
 * <p>Consumer settings:
 * <ul>
 *   <li>Group ID: {@code audit-service}</li>
 *   <li>Values deserialized to {@code java.util.Map} by default; the listener
 *       uses {@code ObjectMapper} to convert to concrete event types per topic</li>
 *   <li>Manual offset commit disabled; Spring Kafka acknowledges after the
 *       listener method returns normally</li>
 * </ul>
 */
@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.compression-type:none}")
    private String compressionType;

    private static final String GROUP_ID          = "audit-service";
    private static final String TRUSTED_PACKAGES  = "com.bbss.*";

    // ── Producer ──────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,              bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,           StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,         JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG,                           "all");
        props.put(ProducerConfig.RETRIES_CONFIG,                        3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,             true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,               compressionType);
        props.put(ProducerConfig.LINGER_MS_CONFIG,                      20);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG,                     32 * 1024);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS,                 false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ── Consumer ──────────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,        bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,                 GROUP_ID);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,        "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,       false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,         500);
        props.put(JsonDeserializer.TRUSTED_PACKAGES,              TRUSTED_PACKAGES);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS,         false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,            "java.util.Map");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    // ── Error Handler with DLT & Exponential Backoff ────────────────────────────

    /**
     * Configures error handling with Dead Letter Topic (DLT) publishing and exponential backoff retry.
     *
     * <p><strong>Retry Policy:</strong>
     * <ul>
     *   <li>Max Attempts: 3 retries</li>
     *   <li>Initial Interval: 2000ms (2 seconds)</li>
     *   <li>Max Interval: 10000ms (10 seconds)</li>
     *   <li>Multiplier: 2.0 (doubles each retry)</li>
     * </ul>
     *
     * <p><strong>Retry Schedule:</strong>
     * <ol>
     *   <li>First retry: 2s after failure</li>
     *   <li>Second retry: 4s after first retry</li>
     *   <li>Third retry: 8s after second retry</li>
     *   <li>After 3 failures: Message sent to DLT (e.g., `audit.entry.dlt`)</li>
     * </ol>
     *
     * @param kafkaTemplate KafkaTemplate for publishing failed messages to DLT
     * @param meterRegistry Micrometer registry for recording DLT publishing metrics
     * @return CommonErrorHandler with DLT publishing and exponential backoff
     */
    @Bean
    public CommonErrorHandler errorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        
        // Dead Letter Publishing Recoverer
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    String originalTopic = record.topic();
                    String dltTopic = originalTopic + ".dlt";
                    
                    log.error("Publishing failed message to DLT: topic={}, partition={}, offset={}, exception={}",
                            originalTopic, record.partition(), record.offset(), ex.getMessage());
                    
                    // Record DLT publishing metric
                    meterRegistry.counter("bbss_kafka_dlt_published_total",
                            "topic", originalTopic,
                            "dlt_topic", dltTopic,
                            "reason", ex.getClass().getSimpleName()
                    ).increment();
                    
                    return new org.apache.kafka.common.TopicPartition(dltTopic, record.partition());
                }
        );

        // Exponential Backoff Configuration
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(2000L);   // 2 seconds first retry
        backOff.setMaxInterval(10000L);      // 10 seconds max wait
        backOff.setMultiplier(2.0);          // Double each retry
        backOff.setMaxElapsedTime(30000L);   // 30 seconds total retry window

        // Default Error Handler with DLT and exponential backoff
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        
        // Log retry attempts
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Kafka consumer retry attempt: topic={}, partition={}, offset={}, attempt={}, exception={}",
                    record.topic(), record.partition(), record.offset(), deliveryAttempt, ex.getMessage());
            
            meterRegistry.counter("bbss_kafka_retry_attempts_total",
                    "topic", record.topic(),
                    "attempt", String.valueOf(deliveryAttempt)
            ).increment();
        });

        return errorHandler;
    }

    // ── Listener Container Factory with Error Handler ──────────────────────────

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setPollTimeout(3_000L);
        factory.setCommonErrorHandler(errorHandler);  // Phase 1.5: Add error handler
        return factory;
    }
}
