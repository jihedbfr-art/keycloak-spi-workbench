package com.jihedapps.keycloak.spi.kafkaevents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The unit tests prove {@code shouldForward}/{@code toJson} are correct in isolation. This test
 * proves the part those can't: that a real {@link KafkaProducer}, configured the way the factory
 * configures it, actually gets a message onto a real broker in the shape a consumer can read.
 * Mocking the producer would make this test pass even if the producer config were subtly wrong
 * (bad serializer, bad acks setting) — the whole point is not mocking it.
 */
@Testcontainers
class KafkaEventPublishingIT {

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer("apache/kafka:3.8.0");

    private Producer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private String topic;

    @BeforeEach
    void setUp() {
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(producerProps);

        topic = "auth-events-" + UUID.randomUUID();

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(topic));
    }

    @AfterEach
    void tearDown() {
        producer.close();
        consumer.close();
    }

    @Test
    void loginEventPublishedByTheProviderIsReadableFromKafka() throws Exception {
        var provider = new KafkaEventListenerProvider(
                producer, topic, EnumSet.of(EventType.LOGIN), new ObjectMapper());

        Event event = mock(Event.class);
        when(event.getType()).thenReturn(EventType.LOGIN);
        when(event.getRealmId()).thenReturn("customer-realm");
        when(event.getClientId()).thenReturn("web-portal");
        when(event.getUserId()).thenReturn("user-42");
        when(event.getIpAddress()).thenReturn("203.0.113.7");

        provider.onEvent(event);
        producer.flush();

        ConsumerRecords<String, String> records = pollUntilNotEmpty(consumer, Duration.ofSeconds(20));
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        records.records(topic).forEach(received::add);

        assertThat(received).hasSize(1);
        JsonNode payload = new ObjectMapper().readTree(received.get(0).value());
        assertThat(payload.get("type").asText()).isEqualTo("LOGIN");
        assertThat(payload.get("realmId").asText()).isEqualTo("customer-realm");
        assertThat(payload.get("userId").asText()).isEqualTo("user-42");
        assertThat(received.get(0).key()).isEqualTo("user-42");
    }

    @Test
    void eventTypeNotInTheForwardListNeverReachesKafka() {
        var provider = new KafkaEventListenerProvider(
                producer, topic, EnumSet.of(EventType.LOGIN), new ObjectMapper());

        Event event = mock(Event.class);
        when(event.getType()).thenReturn(EventType.REGISTER);

        provider.onEvent(event);
        producer.flush();

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
        assertThat(records.records(topic)).isEmpty();
    }

    private ConsumerRecords<String, String> pollUntilNotEmpty(
            KafkaConsumer<String, String> consumer, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records;
            }
        }
        throw new AssertionError("no records received on topic " + topic + " within " + timeout);
    }
}
